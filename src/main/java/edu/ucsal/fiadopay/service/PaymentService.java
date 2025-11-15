package edu.ucsal.fiadopay.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsal.fiadopay.annotations.AntiFraud;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.domain.WebhookDelivery;
import edu.ucsal.fiadopay.dto.request.PaymentRequest;
import edu.ucsal.fiadopay.dto.response.PaymentResponse;
import edu.ucsal.fiadopay.handler.AntiFraudRule;
import edu.ucsal.fiadopay.handler.PaymentHandler;
import edu.ucsal.fiadopay.registry.PaymentHandlerRegistry;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;


@Service
@RequiredArgsConstructor
public class PaymentService {

    private final
    MerchantRepository merchants;
    private final PaymentRepository payments;
    private final
    WebhookDeliveryRepository deliveries;

    private final
    PaymentHandlerRegistry registry;

    private final ExecutorService paymentExecutor;
    private final ExecutorService webhookExecutor;

    private final ObjectMapper objectMapper;

    @Value"${fiadopay.webhook-secret}")
    private String webhookSecret;


    @Value("${fiadopay.processing-delay-ms}")
    private long processingDelayMS;

    @Value("${fiadopay.failure-rate}")
    private double failureRate;

    @Transactional
    public PaymentResponse
    createPayment(String authHeader,

                  String idempotencyKey,

                  PaymentRequest req) {

        Merchant merchant =
                merchantFromAuth
                        (authHeader);
        Long merchantId merchant.getId();

        if (idempotencyKey != null) {
            var existing = payments.findByIdempotencyKeyAndMerchantId
                    (idempotencyKey, merchantId);

            if (existing.isPresent()) {
                return to Response(existing.get());

            }
        }

        Payment payment = Payment.builder().id ("pay_" + UUID.rANDOMuuid().tostring().substring(beginIndex:0,endIndex:8)).
        () -> merchantId(merchantId)
                .method(req.method().toUpperCase()).amount(req.amount())
        () -> () -> currency(req.currency()).installments(req.installments() == null ? 1 : req.installments()).status(Payment.Status.PENDING)
.createdAt(Instant.now()).updatedAt(Instant.now()).idempotencyKey(idempotencyKey).medadataOrderId(req.metadataOrderId()).build();

        PaymentHandler handler = registry.getHandler(req.method());
        if (handler != null) {
            handler.process(payment, req);
        } else {
            payment.setTotalWithInterest(req.amount());
        }

        payments.save(payment);

        paymentExecutor.execute(() -> processAsync(payment));

        return toResponse(payment);
    }

    private void processAsync(Payment payment) {
        sleepSilently(processingDelayMs);

        boolean approved = Math.random() > failureRate;
        Merchant merchant = merchants.findById(payment.getMerchantId()).orElse(null);

        boolean fraud = registry.getFraudRules().stream()
                .anyMatch(rule -> rule.isFraud(payment, merchant));

        payment.setStatus(fraud ? Payment.Status.DECLINED :
                approved ? Payment.Status.APPROVED : Payment.Status.DECLINED);
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);

        sendWebhookAsync(payment);
    }

    public PaymentResponse getPayment(String id) {
        return payments.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public Map<String, Object> refund(String authHeader, String paymentId) {
        Merchant merchant = merchantFromAuth(authHeader);
        Payment p = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!merchant.getId().equals(p.getMerchantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        p.setStatus(Payment.Status.REFUNDED);
        p.setUpdatedAt(Instant.now());
        payments.save(p);

        sendWebhookAsync(p);

        return Map.of(
                "id", "ref_" + UUID.randomUUID().toString().substring(0, 8),
                "status", "PENDING"
        );
    }

    private Merchant merchantFromAuth(String auth) {
        if (auth == null || !auth.startsWith("Bearer FAKE-")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String raw = auth.substring("Bearer FAKE-".length());
        long id;
        try {
            id = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return merchants.findById(id)
                .filter(m -> m.getStatus() == Merchant.Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void sendWebhookAsync(Payment p) {
        webhookExecutor.execute(() -> sendWebhook(p));
    }

    private void sendWebhook(Payment p) {
        Merchant merchant = merchants.findById(p.getMerchantId()).orElse(null);
        if (merchant == null || merchant.getWebhookUrl() == null || merchant.getWebhookUrl().isBlank()) {
            return;
        }

        String payload = buildPayload(p);
        if (payload == null) return;

        String signature = hmac(payload, webhookSecret);

        WebhookDelivery delivery = deliveries.save(WebhookDelivery.builder()
                .eventId("evt_" + UUID.randomUUID().toString().substring(0, 8))
                .eventType("payment.updated")
                .paymentId(p.getId())
                .targetUrl(merchant.getWebhookUrl())
                .signature(signature)
                .payload(payload)
                .attempts(0)
                .delivered(false)
                .lastAttemptAt(null)
                .build());

        webhookExecutor.execute(() -> tryDeliver(delivery.getId()));
    }

    @SneakyThrows
    private void tryDeliver(Long deliveryId) {
        WebhookDelivery d = deliveries.findById(deliveryId).orElse(null);
        if (d == null) return;

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create(d.getTargetUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Event-Type", d.getEventType())
                    .header("X-Signature", d.getSignature())
                    .POST(HttpRequest.BodyPublishers.ofString(d.getPayload()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            d.setAttempts(d.getAttempts() + 1);
            d.setLastAttemptAt(Instant.now());
            d.setDelivered(response.statusCode() >= 200 && response.statusCode() < 300);
            deliveries.save(d);

            if (!d.isDelivered() && d.getAttempts() < 5) {
                Thread.sleep(1000L * d.getAttempts());

                webhookExecutor.execute(() -> tryDeliver(deliveryId));
            }
        } catch (Exception e) {
            d.setAttempts(d.getAttempts() + 1);
            d.setLastAttemptAt(Instant.now());
            d.setDelivered(false);
            deliveries.save(d);

            if (d.getAttempts() < 5) {
                Thread.sleep(1000L * d.getAttempts());
                webhookExecutor.execute(() -> tryDeliver(deliveryId));
            }
        }
    }

    private String buildPayload(Payment p) {
        try {
            Map<String, Object> data = Map.of(
                    "paymentId", p.getId(),
                    "status", p.getStatus().name(),
                    "occurredAt", Instant.now().toString()
            );
            Map<String, Object> event = Map.of(
                    "id", "evt_" + UUID.randomUUID().toString().substring(0, 8),
                    "type", "payment.updated",
                    "data", data
            );
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return null;
        }
    }

    private static String hmac(String payload, String secret) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes()));
        } catch (Exception e) {
            return "";
        }
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getStatus().name(),
                p.getMethod(),
                p.getAmount(),
                p.getInstallments(),
                p.getMonthlyInterest(),
                p.getTotalWithInterest()
        );
    }

    private void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}




