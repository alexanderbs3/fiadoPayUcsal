# FiadoPay Simulator

## 📋 Sumário Executivo

**FiadoPay Simulator** é um sistema de gateway de pagamentos educacional desenvolvido em Java 21 com Spring Boot 3.5.7. O projeto simula o comportamento de plataformas de processamento de pagamentos (como Stripe, PagSeguro, Mercado Pago), implementando conceitos avançados de engenharia de software: autenticação OAuth2, processamento assíncrono, webhooks com retry exponencial, antifraude customizável via anotações, e idempotência.

---

## 🎯 Objetivos do Projeto

- Demonstrar arquitetura de microserviços para gateways de pagamento
- Implementar padrões de projeto (Strategy, Registry, Observer)
- Aplicar programação reflexiva com anotações customizadas
- Gerenciar concorrência com thread pools dedicados
- Simular fluxos reais de pagamento (aprovação, recusa, webhook, retry)

---

## 🏗️ Arquitetura Geral

```
┌─────────────────────────────────────────────────────────────┐
│                      FiadoPay Gateway API                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   AuthN/Z    │  │   Payment    │  │   Webhook    │      │
│  │ (OAuth Fake) │  │  Processing  │  │   Delivery   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│         │                  │                  │              │
│         ▼                  ▼                  ▼              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           Spring Boot Application Context            │  │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐     │  │
│  │  │  Payment   │  │ AntiFraud  │  │  Webhook   │     │  │
│  │  │  Registry  │  │   Rules    │  │  Executor  │     │  │
│  │  └────────────┘  └────────────┘  └────────────┘     │  │
│  └──────────────────────────────────────────────────────┘  │
│                           │                                  │
│                           ▼                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              H2 In-Memory Database                    │  │
│  │  [Merchants] [Payments] [WebhookDelivery]            │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 📂 Estrutura de Pacotes

```
edu.ucsal.fiadopay/
├── annotations/              # Anotações customizadas (@PaymentMethod, @AntiFraud)
│   ├── AntiFraud.java       # Marca regras de antifraude
│   ├── PaymentMethod.java   # Marca handlers de pagamento (CARD, PIX, etc.)
│   └── WebhookSink.java     # (Reservado para futuras extensões)
│
├── config/                   # Configurações Spring
│   ├── AsyncConfig.java     # Thread pools (paymentExecutor, webhookExecutor)
│   └── OpenApiConfig.java   # Swagger/OpenAPI (documentação automática)
│
├── controller/              # Camada REST API
│   ├── AuthController.java        # POST /auth/token (OAuth fake)
│   ├── HealthController.java      # GET /health (healthcheck)
│   ├── MerchantAdminController.java  # POST /admin/merchants (cadastro)
│   └── PaymentController.java     # POST/GET /gateway/payments, POST /refunds
│
├── domain/                  # Entidades JPA
│   ├── Merchant.java        # Lojista (clientId, secret, webhookUrl)
│   ├── Payment.java         # Transação (status, método, valores)
│   └── WebhookDelivery.java # Log de tentativas de webhook
│
├── dto/                     # Data Transfer Objects
│   ├── request/
│   │   ├── PaymentRequest.java
│   │   ├── RefundRequest.java
│   │   └── TokenRequest.java
│   └── response/
│       ├── MerchantCreateDTO.java
│       ├── PaymentResponse.java
│       └── TokenResponse.java
│
├── handler/                 # Handlers de negócio (Strategy Pattern)
│   ├── AntiFraudRule.java           # Interface para regras de fraude
│   ├── PaymentHandler.java          # Interface para processadores de pagamento
│   ├── CardPaymentHandler.java      # Implementa juros parcelados
│   ├── PixPaymentHandler.java       # Sem juros
│   └── HighAmountFraudRule.java     # Regra: >R$ 5.000,00 = fraude
│
├── registry/                # Registry Pattern (descoberta dinâmica)
│   └── PaymentHandlerRegistry.java  # Mapeia handlers e regras via anotações
│
├── repo/                    # Repositórios JPA
│   ├── MerchantRepository.java
│   ├── PaymentRepository.java
│   └── WebhookDeliveryRepository.java
│
├── service/                 # Lógica de negócio
│   └── PaymentService.java  # Orquestra pagamento, antifraude, webhook
│
└── FiadoPayApplication.java # Classe principal (@SpringBootApplication)
```

---

## 🔑 Principais Componentes

### 1. **Autenticação (OAuth2 Fake)**
- **Endpoint:** `POST /fiadopay/auth/token`
- **Fluxo:** Cliente envia `client_id` e `client_secret` → API retorna token `Bearer FAKE-{merchantId}`
- **Validação:** Verifica se merchant existe e está ativo (`Status.ACTIVE`)

### 2. **Processamento de Pagamentos**
- **Endpoint:** `POST /fiadopay/gateway/payments`
- **Headers:**
    - `Authorization: Bearer FAKE-{id}` (obrigatório)
    - `Idempotency-Key` (opcional, evita duplicação)
- **Body:**
  ```json
  {
    "method": "CARD",
    "currency": "BRL",
    "amount": 1500.00,
    "installments": 3,
    "metadataOrderId": "ORDER-12345"
  }
  ```
- **Fluxo:**
    1. Valida idempotência (se key fornecida, retorna pagamento existente)
    2. Cria entidade `Payment` com status `PENDING`
    3. Aplica handler específico do método (`CardPaymentHandler` calcula juros)
    4. Salva no banco
    5. Dispara processamento assíncrono em thread pool dedicada

### 3. **Processamento Assíncrono**
- **Thread Pool:** `paymentExecutor` (pool fixo com N_CPUs threads)
- **Delay Simulado:** 1500ms (configurável em `application.yml`)
- **Lógica:**
    1. Aguarda delay
    2. Simula aprovação/recusa (85% aprovado, 15% recusado)
    3. Executa regras de antifraude (via `PaymentHandlerRegistry`)
    4. Atualiza status: `APPROVED` | `DECLINED`
    5. Dispara webhook

### 4. **Sistema de Antifraude (Extensível)**
- **Anotação:** `@AntiFraud(name="...", threshold=...)`
- **Exemplo:**
  ```java
  @Service
  @AntiFraud(name = "HighAmount", threshold = 5_000.0)
  public class HighAmountFraudRule implements AntiFraudRule {
      boolean isFraud(Payment p, Merchant m) {
          return p.getAmount().doubleValue() > 5_000.0;
      }
  }
  ```
- **Descoberta:** Ao iniciar, `PaymentHandlerRegistry` varre beans anotados
- **Execução:** Todas as regras são aplicadas; se alguma retornar `true`, pagamento é recusado

### 5. **Webhooks com Retry Exponencial**
- **Thread Pool:** `webhookExecutor` (10 threads fixas)
- **Payload:**
  ```json
  {
    "id": "evt_abc123",
    "type": "payment.updated",
    "data": {
      "paymentId": "pay_xyz",
      "status": "APPROVED",
      "occurredAt": "2025-11-18T10:30:00Z"
    }
  }
  ```
- **Headers:**
    - `X-Event-Type: payment.updated`
    - `X-Signature: {HMAC-SHA256}` (usando `fiadopay.webhook-secret`)
- **Retry:**
    - Até 5 tentativas
    - Back-off: 1s, 2s, 3s, 4s, 5s
    - Se status HTTP 2xx → marca como entregue

### 6. **Idempotência**
- Constraint única no banco: `(merchantId, idempotencyKey)`
- Se key duplicada, retorna pagamento existente (HTTP 201)
- Evita double-charging em network retries

---

## 🧩 Padrões de Projeto Implementados

| Padrão | Implementação | Benefício |
|--------|---------------|-----------|
| **Strategy** | `PaymentHandler` interface com múltiplas implementações (`CardPaymentHandler`, `PixPaymentHandler`) | Facilita adição de novos métodos sem modificar código existente |
| **Registry** | `PaymentHandlerRegistry` descobre handlers via reflexão e anotações | Desacoplamento e extensibilidade |
| **Template Method** | `tryDeliver()` com retry exponencial | Reutilização de lógica de retry |
| **Builder** | `Payment.builder()`, `Merchant.builder()` (Lombok) | Código mais legível para objetos complexos |
| **Dependency Injection** | Spring IoC gerencia todas as dependências | Facilita testes e manutenção |

---

## ⚙️ Configuração e Execução

### Pré-requisitos
- Java 21+ (JDK)
- Maven 3.8+

### Instalação

```bash
# Clone o repositório
git clone <repo-url>
cd fiadopay-sim

# Compile e execute
mvn clean install
mvn spring-boot:run
```

### Portas e URLs

| Serviço | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| H2 Console | http://localhost:8080/h2 (JDBC URL: `jdbc:h2:mem:fiadopay`) |

### Configurações (`application.yml`)

```yaml
fiadopay:
  webhook-secret: ucsal-2025          # Segredo HMAC para assinatura de webhooks
  processing-delay-ms: 1500           # Delay simulado no processamento
  failure-rate: 0.15                  # Taxa de recusa aleatória (15%)
```

---

## 🚀 Fluxo de Uso Completo

### 1. Criar Merchant
```bash
curl -X POST http://localhost:8080/fiadopay/admin/merchants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Loja do João",
    "webhookUrl": "https://webhook.site/abc123"
  }'
```
**Resposta:**
```json
{
  "id": 1,
  "name": "Loja do João",
  "clientId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "clientSecret": "9b3c8f7d4e6a2b1c5f8e7d6a3b2c1f4e",
  "webhookUrl": "https://webhook.site/abc123",
  "status": "ACTIVE"
}
```

### 2. Obter Token
```bash
curl -X POST http://localhost:8080/fiadopay/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "client_secret": "9b3c8f7d4e6a2b1c5f8e7d6a3b2c1f4e"
  }'
```
**Resposta:**
```json
{
  "access_token": "FAKE-1",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### 3. Criar Pagamento
```bash
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -H "Idempotency-Key: ORDER-001" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "CARD",
    "currency": "BRL",
    "amount": 1000.00,
    "installments": 3,
    "metadataOrderId": "ORDER-001"
  }'
```
**Resposta (imediata):**
```json
{
  "id": "pay_a1b2c3d4",
  "status": "PENDING",
  "method": "CARD",
  "amount": 1000.00,
  "installments": 3,
  "interestRate": 1.0,
  "total": 1030.30
}
```

### 4. Consultar Status (após 1.5s)
```bash
curl http://localhost:8080/fiadopay/gateway/payments/pay_a1b2c3d4
```
**Resposta:**
```json
{
  "id": "pay_a1b2c3d4",
  "status": "APPROVED",
  "method": "CARD",
  "amount": 1000.00,
  "installments": 3,
  "interestRate": 1.0,
  "total": 1030.30
}
```

### 5. Webhook Recebido (no endpoint do merchant)
```http
POST https://webhook.site/abc123
X-Event-Type: payment.updated
X-Signature: dGVzdA==... (HMAC-SHA256)

{
  "id": "evt_xyz123",
  "type": "payment.updated",
  "data": {
    "paymentId": "pay_a1b2c3d4",
    "status": "APPROVED",
    "occurredAt": "2025-11-18T14:23:45Z"
  }
}
```

---

## 🔍 Conceitos Técnicos Avançados

### 1. **Programação Reflexiva com Anotações**
- Spring IoC varre beans no contexto
- `@PostConstruct` em `PaymentHandlerRegistry` coleta handlers anotados
- Permite adicionar novos handlers sem modificar registry

### 2. **Thread Pools Dedicados**
```java
// AsyncConfig.java
@Bean
public ExecutorService paymentExecutor() {
    return Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    );
}
```
- `paymentExecutor`: processa aprovações/recusas
- `webhookExecutor`: envia webhooks (isolamento de responsabilidades)

### 3. **Idempotência com Constraint Única**
```java
@UniqueConstraint(
    name = "uk_payment_merchant_idempotency",
    columnNames = {"merchantId", "idempotencyKey"}
)
```
- Garante que mesmo `Idempotency-Key` não cria pagamento duplicado
- Padrão essencial em APIs de pagamento (previne double-charge)

### 4. **HMAC para Assinatura de Webhooks**
```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
String signature = Base64.getEncoder().encodeToString(
    mac.doFinal(payload.getBytes())
);
```
- Merchant valida que webhook veio realmente do gateway
- Previne spoofing de notificações

### 5. **Retry Exponencial com Back-off**
```java
if (!delivered && attempts < 5) {
    Thread.sleep(1000L * attempts); // 1s, 2s, 3s...
    webhookExecutor.execute(() -> tryDeliver(deliveryId));
}
```
- Reduz carga em falhas temporárias
- Comum em sistemas distribuídos (Netflix Hystrix, AWS Lambda Retry)

---

## 📊 Diagrama de Sequência (Fluxo de Pagamento)

```
Cliente         API          PaymentService    PaymentExecutor    Merchant Webhook
  │              │                  │                  │                 │
  ├─POST────────>│                  │                  │                 │
  │ /payments    │                  │                  │                 │
  │              ├─createPayment──>│                  │                 │
  │              │                  ├─save(PENDING)──>│                 │
  │              │                  │                  │                 │
  │<─201─────────┤                  │                  │                 │
  │ {status:     │                  ├──execute()─────>│                 │
  │  PENDING}    │                  │                  │                 │
  │              │                  │                 [sleep 1.5s]       │
  │              │                  │                  │                 │
  │              │                  │                 ├─antifraude()     │
  │              │                  │                 ├─save(APPROVED)   │
  │              │                  │                 ├─sendWebhook()───>│
  │              │                  │                  │                 │
  │──GET──────>│                  │                  │                 │
  │ /payments/X  │                  │                  │                 │
  │              ├─getPayment()────>│                  │                 │
  │<─200─────────┤                  │                  │                 │
  │ {status:     │                  │                  │                 │
  │  APPROVED}   │                  │                  │                 │
```

---

## 🧪 Testes e Validação

### Cenários de Teste

| Cenário | Entrada | Saída Esperada |
|---------|---------|----------------|
| Pagamento aprovado | `amount=500`, `method=PIX` | `status=APPROVED` |
| Pagamento recusado (fraude) | `amount=6000`, `method=CARD` | `status=DECLINED` |
| Parcelamento | `installments=6`, `amount=1200` | `total=1273.45` (juros 1%/mês) |
| Idempotência | Mesma `Idempotency-Key` 2x | Retorna mesmo `paymentId` |
| Webhook retry | Endpoint offline | 5 tentativas com back-off |

### Comandos úteis

```bash
# Verificar logs de processamento
tail -f logs/spring.log

# Acessar H2 Console (verificar WebhookDelivery)
# URL: http://localhost:8080/h2
# JDBC URL: jdbc:h2:mem:fiadopay
# User: sa / Password: (vazio)

# Testar webhook com webhook.site
# 1. Acesse https://webhook.site
# 2. Copie URL única
# 3. Use no campo webhookUrl ao criar merchant
```

---

## 🔒 Segurança (Limitações Conhecidas)

> ⚠️ **Este é um projeto EDUCACIONAL. NÃO usar em produção.**

- Token `Bearer FAKE-{id}` é previsível
- Sem rate limiting
- Sem criptografia de dados sensíveis
- H2 in-memory (dados perdidos ao reiniciar)
- Sem validação de certificados SSL em webhooks

### Melhorias para Produção

1. **JWT real** com assinatura RSA/ECDSA
2. **PostgreSQL/MySQL** com Flyway para migrations
3. **Spring Security** com OAuth2
4. **Rate Limiting** (Bucket4j, Redis)
5. **Circuit Breaker** (Resilience4j) para webhooks
6. **Observabilidade** (Micrometer + Prometheus + Grafana)
7. **Testes** (JUnit 5, Testcontainers, WireMock)

---

## 📚 Dependências Principais

| Biblioteca | Versão | Uso |
|------------|--------|-----|
| Spring Boot | 3.5.7 | Framework base |
| Spring Data JPA | (incluso) | Persistência ORM |
| H2 Database | runtime | Banco in-memory |
| Lombok | optional | Redução de boilerplate |
| SpringDoc OpenAPI | 2.8.13 | Documentação Swagger |
| Jakarta Validation | (incluso) | Validação de DTOs |

---

## 🤝 Contribuindo

1. Fork o projeto
2. Crie branch: `git checkout -b feature/nova-funcionalidade`
3. Commit: `git commit -m 'Adiciona handler BOLETO'`
4. Push: `git push origin feature/nova-funcionalidade`
5. Abra Pull Request

### Exemplos de Extensões

- Adicionar método `BOLETO` (vencimento 3 dias, sem juros)
- Implementar cache Redis para tokens
- Adicionar API de consulta de saldo do merchant
- Webhook com retentativa via fila (RabbitMQ/SQS)

---

## 📖 Referências

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [Stripe API Design](https://stripe.com/docs/api)
- [RFC 7807 - Problem Details](https://datatracker.ietf.org/doc/html/rfc7807)
- [HMAC-SHA256 Specification](https://datatracker.ietf.org/doc/html/rfc2104)
- [Idempotent Requests Pattern](https://brandur.org/idempotency-keys)

---

## 📝 Licença

Projeto educacional desenvolvido para fins acadêmicos na Universidade Católica do Salvador (UCSAL).

---

## 👨‍💻 Autor

**Equipe UCSAL 2025**  
Curso: Engenharia de Software  
Disciplina: Arquitetura de Microserviços

---

## 🆘 Troubleshooting

### Problema: "Port 8080 already in use"
```bash
# Linux/Mac
lsof -ti:8080 | xargs kill -9

# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Problema: Webhook não chega
1. Verificar se `webhookUrl` está acessível publicamente
2. Usar serviços como `ngrok` ou `webhook.site` para testes
3. Checar tabela `WEBHOOK_DELIVERY` no H2 Console

### Problema: Todos pagamentos são recusados
- Verificar `failure-rate` em `application.yml`
- Desabilitar temporariamente regras de antifraude

---

## 📈 Roadmap

- [ ] Implementar API de consulta de histórico de transações
- [ ] Adicionar métricas (Actuator + Micrometer)
- [ ] Dashboard React para visualizar pagamentos
- [ ] Suporte a múltiplas moedas (USD, EUR)
- [ ] Sistema de disputa (chargebacks)
- [ ] Integração com gateway real (Stripe/PayPal em sandbox)

---
