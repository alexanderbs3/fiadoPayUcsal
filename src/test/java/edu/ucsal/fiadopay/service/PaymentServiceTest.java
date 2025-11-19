package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.dto.request.PaymentRequest;
import edu.ucsal.fiadopay.dto.response.PaymentResponse;
import edu.ucsal.fiadopay.handler.PaymentHandler;
import edu.ucsal.fiadopay.registry.PaymentHandlerRegistry;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService - Testes Unitários")
class PaymentServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private PaymentHandlerRegistry registry;

    @Mock
    private ExecutorService paymentExecutor;

    @Mock
    private ExecutorService webhookExecutor;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    private Merchant mockMerchant;
    private PaymentRequest validRequest;

    @BeforeEach
    void setUp() {
        // Configura propriedades via reflection (simula application.yml)
        ReflectionTestUtils.setField(paymentService, "webhookSecret", "test-secret");
        ReflectionTestUtils.setField(paymentService, "processingDelayMs", 100L);
        ReflectionTestUtils.setField(paymentService, "failureRate", 0.0);

        // Mock merchant válido
        mockMerchant = Merchant.builder()
                .id(1L)
                .name("Test Merchant")
                .clientId("test-client-id")
                .clientSecret("test-secret")
                .webhookUrl("https://webhook.test")
                .status(Merchant.Status.ACTIVE)
                .build();

        // Request válido
        validRequest = new PaymentRequest(
                "CARD",
                "BRL",
                new BigDecimal("100.00"),
                3,
                "ORDER-123"
        );
    }

    @Test
    @DisplayName("Deve criar pagamento com sucesso")
    void shouldCreatePaymentSuccessfully() {
        // Arrange
        String authHeader = "Bearer FAKE-1";
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(mockMerchant));
        when(paymentRepository.findByIdempotencyKeyAndMerchantId(anyString(), anyLong()))
                .thenReturn(Optional.empty());

        PaymentHandler mockHandler = mock(PaymentHandler.class);
        when(registry.getHandler("CARD")).thenReturn(mockHandler);
        when(mockHandler.process(any(), any())).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setTotalWithInterest(new BigDecimal("103.03"));
            return p;
        });

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(paymentExecutor).execute(any(Runnable.class));

        // Act
        PaymentResponse response = paymentService.createPayment(authHeader, "IDEM-123", validRequest);

        // Assert
        assertNotNull(response);
        assertEquals("PENDING", response.status());
        assertEquals("CARD", response.method());
        assertEquals(new BigDecimal("100.00"), response.amount());
        assertEquals(3, response.installments());
        assertEquals(new BigDecimal("103.03"), response.total());

        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(paymentExecutor, times(1)).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("Deve retornar pagamento existente quando usar mesma idempotency key")
    void shouldReturnExistingPaymentForSameIdempotencyKey() {
        // Arrange
        String authHeader = "Bearer FAKE-1";
        String idempotencyKey = "IDEM-123";

        Payment existingPayment = Payment.builder()
                .id("pay_existing")
                .merchantId(1L)
                .method("CARD")
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .installments(3)
                .totalWithInterest(new BigDecimal("103.03"))
                .status(Payment.Status.APPROVED)
                .build();

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(mockMerchant));
        when(paymentRepository.findByIdempotencyKeyAndMerchantId(idempotencyKey, 1L))
                .thenReturn(Optional.of(existingPayment));

        // Act
        PaymentResponse response = paymentService.createPayment(authHeader, idempotencyKey, validRequest);

        // Assert
        assertNotNull(response);
        assertEquals("pay_existing", response.id());
        assertEquals("APPROVED", response.status());

        verify(paymentRepository, never()).save(any());
        verify(paymentExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("Deve lançar exceção 401 para token inválido")
    void shouldThrow401ForInvalidToken() {
        // Arrange
        String invalidAuthHeader = "Bearer INVALID-TOKEN";

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(invalidAuthHeader, null, validRequest)
        );

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    @DisplayName("Deve lançar exceção 401 para merchant inativo")
    void shouldThrow401ForInactiveMerchant() {
        // Arrange
        String authHeader = "Bearer FAKE-1";
        mockMerchant.setStatus(Merchant.Status.BLOCKED);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(mockMerchant));

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(authHeader, null, validRequest)
        );

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    @DisplayName("Deve buscar pagamento por ID com sucesso")
    void shouldGetPaymentById() {
        // Arrange
        Payment payment = Payment.builder()
                .id("pay_123")
                .merchantId(1L)
                .method("PIX")
                .amount(new BigDecimal("50.00"))
                .currency("BRL")
                .installments(1)
                .totalWithInterest(new BigDecimal("50.00"))
                .status(Payment.Status.APPROVED)
                .build();

        when(paymentRepository.findById("pay_123")).thenReturn(Optional.of(payment));

        // Act
        PaymentResponse response = paymentService.getPayment("pay_123");

        // Assert
        assertNotNull(response);
        assertEquals("pay_123", response.id());
        assertEquals("APPROVED", response.status());
        assertEquals("PIX", response.method());
    }

    @Test
    @DisplayName("Deve lançar exceção 404 para pagamento não encontrado")
    void shouldThrow404ForPaymentNotFound() {
        // Arrange
        when(paymentRepository.findById("pay_nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.getPayment("pay_nonexistent")
        );

        assertEquals(404, exception.getStatusCode().value());
    }
}