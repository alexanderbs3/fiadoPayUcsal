# 💳 FiadoPay — Simulador de Gateway de Pagamentos

## 🧭 Resumo Executivo
**FiadoPay** é um simulador educacional de gateway de pagamentos desenvolvido em **Spring Boot**, que implementa um fluxo completo de processamento de transações, incluindo:

- Autenticação **OAuth2-like**
- Processamento **assíncrono**
- **Antifraude plugável** via anotações customizadas
- **Webhooks** com retry exponencial
- API RESTful documentada com **OpenAPI/Swagger**

---

## 📚 Índice
1. [Visão Geral](#-visão-geral)
2. [Arquitetura e Componentes](#-arquitetura-e-componentes)
3. [Funcionalidades Principais](#-funcionalidades-principais)
4. [Stack Tecnológica](#-stack-tecnológica)
5. [Pré-requisitos](#-pré-requisitos)
6. [Instalação e Configuração](#-instalação-e-configuração)
7. [Uso da API](#-uso-da-api)
8. [Estrutura do Projeto](#-estrutura-do-projeto)
9. [Padrões de Design Implementados](#-padrões-de-design-implementados)
10. [Configurações Avançadas](#-configurações-avançadas)
11. [Limitações e Roadmap](#-limitações-e-roadmap)
12. [Troubleshooting](#-troubleshooting)
13. [Contribuindo](#-contribuindo)
14. [Licença](#-licença)
15. [Contato](#-contato)
16. [Referências Técnicas](#-referências-técnicas)

---

## 🚀 Visão Geral
O **FiadoPay** simula o comportamento de gateways reais (como Stripe, PayPal ou PagSeguro), oferecendo:

- Múltiplos métodos de pagamento: **CARD**, **PIX**, **BOLETO**, **DEBIT**
- Sistema antifraude modular via **anotações customizadas**
- Processamento **assíncrono**
- Webhooks com **retry exponencial e assinatura HMAC SHA-256**
- **Idempotência** para evitar duplicações
- Autenticação **OAuth2-like** (tokens fakes para uso educacional)

---

## 🧩 Arquitetura e Componentes

### Diagrama de Fluxo Principal
┌─────────────┐ ┌──────────────┐ ┌─────────────────┐
│ Cliente │─────▶│ AuthController│─────▶│ MerchantRepo │
│ (Merchant) │ │ /auth/token │ │ (Validação) │
└─────────────┘ └──────────────┘ └─────────────────┘
│
▼
┌─────────────────────────────────────────────────────────────┐
│ PaymentController (/gateway/payments) │
└─────────────────────────────────────────────────────────────┘


---

## ⚙️ Componentes Principais

| Componente | Responsabilidade | Padrão Aplicado |
|-------------|------------------|-----------------|
| `PaymentHandlerRegistry` | Descobre e registra handlers via reflexão | Registry + Strategy |
| `PaymentHandler` | Interface de processamento de métodos de pagamento | Strategy |
| `AntiFraudRule` | Interface para regras antifraude | Chain of Responsibility |
| `AsyncConfig` | Configuração de thread pools | Thread Pool |
| `PaymentService` | Orquestra o fluxo de pagamento | Service Layer |
| `@PaymentMethod`, `@AntiFraud`, `@WebhookSink` | Metaprogramação | Anotações Customizadas |

---

## 💡 Funcionalidades Principais

### 1. Criação de Pagamento
```http
POST /fiadopay/gateway/payments
Authorization: Bearer FAKE-1
Idempotency-Key: uuid
Content-Type: application/json

Fluxo Interno

Valida token (FAKE-{merchantId})

Checa idempotência

Aplica handler específico

Salva pagamento PENDING

Processamento assíncrono

Resposta imediata

Juros (cartão parcelado)

1% ao mês → total = amount × 1.01^installments
R$150,00 em 3x = R$154,54


2. Sistema Antifraude

Regra atual: HighAmountFraudRule

Declina automaticamente pagamentos acima de R$5.000,00

Exemplo de nova regra:

@Service
@AntiFraud(name = "VelocityCheck", threshold = 10.0)
public class VelocityFraudRule implements AntiFraudRule {
    @Override
    public boolean isFraud(Payment p, Merchant m) {
        // Ex: mais de 10 transações em 1 minuto
        return false;
    }
}

3. Webhooks com Retry

Payload

{
  "id": "evt_a3f2b1c9",
  "type": "payment.updated",
  "data": {
    "paymentId": "pay_7d4e2f1a",
    "status": "APPROVED",
    "occurredAt": "2025-11-11T14:30:00Z"
  }
}
Retry Policy

Tentativas: 5

Backoff: exponencial (1s, 2s, 3s, 4s, 5s)

Thread pool: webhookExecutor (10 threads)

4. Autenticação Simplificada
POST /fiadopay/auth/token
Content-Type: application/json

{
  "client_id": "uuid-do-merchant",
  "client_secret": "secret-gerado"
}
Resposta:
{
  "access_token": "FAKE-1",
  "token_type": "Bearer",
  "expires_in": 3600
}

🧱 Stack Tecnológica
Tecnologia	Uso
Java 17+	Linguagem base
Spring Boot 3.x	Framework web
Spring Data JPA	Persistência ORM
H2 Database	Banco em memória
Lombok	Redução de boilerplate
Springdoc OpenAPI	Swagger
Jakarta Validation	Bean Validation
Java HttpClient	Webhooks

🧰 Pré-requisitos
java -version   # deve mostrar "17" ou superior
mvn -version    # confirma Maven 3.8+

🧩 Instalação e Configuração
# 1. Clonar
git clone https://github.com/seu-usuario/fiadopay.git
cd fiadopay

# 2. Compilar
mvn clean install

# 3. Executar
mvn spring-boot:run
Acesso Rápido

API Base → http://localhost:8080/fiadopay

Swagger UI → http://localhost:8080/swagger-ui.html

Console H2 → http://localhost:8080/h2

🗂️ Estrutura do Projeto
edu.ucsal.fiadopay/
├── annotations/
├── config/
├── controller/
├── domain/
├── dto/
├── handler/
├── registry/
├── repo/
├── service/
└── FiadoPayApplication.java

🧠 Padrões de Design Implementados

Strategy → PaymentHandler

Registry → PaymentHandlerRegistry

Chain of Responsibility → AntiFraudRule

Thread Pool → AsyncConfig

Template Method → Webhook retry

⚙️ Configurações Avançadas

Alterar taxa de falhas

fiadopay:
  failure-rate: 0.3


Delay de processamento

fiadopay:
  processing-delay-ms: 5000


Banco PostgreSQL

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/fiadopay
    username: postgres
    password: senha123

🧩 Limitações e Roadmap
Aspecto	Limitação	Impacto
Autenticação	Token fake	🔴 Não usar em produção
Persistência	H2 in-memory	🟡 OK para testes
Observabilidade	Sem métricas	🟡 Manual
Concorrência	Sem locks otimizados	🟡 Possíveis race conditions

Roadmap

v2.0 → JWT real

v2.1 → PostgreSQL/MySQL

v2.3 → Métricas Prometheus

v2.4 → Docker + Kubernetes

v3.0 → Split Payments

🧾 Troubleshooting

Pagamentos travados em PENDING → Verificar thread pool e logs.

Webhooks falhando → checar rede/firewall.

Erro 401 → Token inválido ou merchant inativo.

🧩 Contribuindo

Para adicionar novo método de pagamento:
@Service
@PaymentMethod("CRYPTO")
public class CryptoPaymentHandler implements PaymentHandler {
    @Override
    public Payment process(Payment p, PaymentRequest req) {
        double btcRate = 250_000.0;
        double btcAmount = req.amount().doubleValue() / btcRate;
        p.setTotalWithInterest(req.amount());
        return p;
    }
}

⚖️ Licença

Projeto educacional, desenvolvido para fins acadêmicos na UCSAL - Universidade Católica do Salvador.
Não utilizar em produção sem implementar:

OAuth2 + JWT

Criptografia (PCI-DSS)

Auditoria de transações

LGPD/GDPR compliance

👤 Contato

Desenvolvido por: [Alexander Costa, Alice , Andra, Gabriela, Washigton Jesus]
E-mail: contato@seudominio.com

Repositório: github.com/seu-usuario/fiadopay
# fiadoPayUcsal
