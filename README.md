# 💳 FiadoPay Simulator

> **Simulador educacional de gateway de pagamentos** para aprender arquitetura de microserviços, Spring Boot avançado e padrões de projeto reais.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Educational-blue.svg)]()

---

## 🎯 O que é este projeto?

**FiadoPay** é um simulador que reproduz o funcionamento de plataformas como **Stripe**, **PagSeguro** ou **Mercado Pago**. Ele implementa:

✅ Processamento de pagamentos (Cartão, PIX, Débito, Boleto)  
✅ Sistema de autenticação OAuth2 simplificado  
✅ Detecção de fraudes customizável  
✅ Webhooks com retry automático  
✅ Processamento assíncrono com thread pools  
✅ Idempotência (evita cobranças duplicadas)

### 🎓 Público-alvo
- **Estudantes** aprendendo Spring Boot e microserviços
- **Desenvolvedores** querendo entender gateways de pagamento
- **Engenheiros** estudando padrões de projeto em Java

---

## 📚 Pré-requisitos

### Conhecimentos esperados
- ✅ Java básico (classes, interfaces, herança)
- ✅ Spring Boot básico (controllers, services)
- ⚠️ **Não precisa saber:** Anotações customizadas, thread pools, webhooks (o projeto ensina isso!)

### Software necessário
```bash
# Verifique se tem Java 21+
java -version  # Deve mostrar "version 21" ou superior

# Verifique se tem Maven
mvn -version   # Deve mostrar "Apache Maven 3.x"
```

**Não tem instalado?**
- **Java 21:** [Download do OpenJDK](https://adoptium.net/)
- **Maven:** [Guia de instalação](https://maven.apache.org/install.html)

---

## 🚀 Como começar (Passo a passo)

### Passo 1: Clone e execute

```bash
# 1. Clone o repositório
git clone https://github.com/seu-usuario/fiadopay-sim.git
cd fiadopay-sim

# 2. Compile e baixe dependências (pode demorar 2-3 minutos na primeira vez)
mvn clean install

# 3. Inicie a aplicação
mvn spring-boot:run

# ✅ Se aparecer "Started FiadoPayApplication in X seconds", está funcionando!
```

### Passo 2: Acesse a documentação interativa

Abra no navegador: **http://localhost:8080/swagger-ui.html**

Você verá uma interface visual com todos os endpoints da API:

```
┌─────────────────────────────────────────────────┐
│  📄 FiadoPay Simulator API - v1                 │
│                                                  │
│  🔐 AuthController                              │
│     POST /fiadopay/auth/token                   │
│                                                  │
│  💰 PaymentController                           │
│     POST /fiadopay/gateway/payments             │
│     GET  /fiadopay/gateway/payments/{id}        │
│     POST /fiadopay/gateway/refunds              │
│                                                  │
│  🏪 MerchantAdminController                     │
│     POST /fiadopay/admin/merchants              │
└─────────────────────────────────────────────────┘
```

### Passo 3: Teste seu primeiro pagamento

Abra um terminal e execute os comandos abaixo (ou use o Swagger UI):

#### 3.1. Crie um lojista (merchant)

```bash
curl -X POST http://localhost:8080/fiadopay/admin/merchants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Pizzaria do Zé",
    "webhookUrl": "https://webhook.site/unique-id"
  }'
```

**💡 Dica:** Acesse [webhook.site](https://webhook.site) e copie sua URL única antes de executar.

**Resposta esperada:**
```json
{
  "id": 1,
  "name": "Pizzaria do Zé",
  "clientId": "abc-123-def-456",  ← Copie este valor
  "clientSecret": "xyz789",       ← Copie este valor
  "webhookUrl": "https://webhook.site/...",
  "status": "ACTIVE"
}
```

#### 3.2. Obtenha um token de autenticação

```bash
curl -X POST http://localhost:8080/fiadopay/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "abc-123-def-456",     ← Cole aqui o clientId
    "client_secret": "xyz789"           ← Cole aqui o clientSecret
  }'
```

**Resposta esperada:**
```json
{
  "access_token": "FAKE-1",  ← Copie este token
  "token_type": "Bearer",
  "expires_in": 3600
}
```

#### 3.3. Crie um pagamento

```bash
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1"  ← Cole aqui o token
  -H "Content-Type: application/json" \
  -d '{
    "method": "CARD",
    "currency": "BRL",
    "amount": 100.00,
    "installments": 3
  }'
```

**Resposta IMEDIATA (status=PENDING):**
```json
{
  "id": "pay_a1b2c3d4",
  "status": "PENDING",       ← Aguardando processamento
  "method": "CARD",
  "amount": 100.00,
  "installments": 3,
  "interestRate": 1.0,       ← 1% ao mês
  "total": 103.03            ← 100 × 1.01³
}
```

#### 3.4. Aguarde 2 segundos e consulte novamente

```bash
# Aguarde 2 segundos...
sleep 2

curl http://localhost:8080/fiadopay/gateway/payments/pay_a1b2c3d4
```

**Resposta FINAL (status=APPROVED ou DECLINED):**
```json
{
  "id": "pay_a1b2c3d4",
  "status": "APPROVED",      ← Pagamento aprovado!
  "method": "CARD",
  "amount": 100.00,
  "installments": 3,
  "interestRate": 1.0,
  "total": 103.03
}
```

#### 3.5. Verifique o webhook

Volte para **webhook.site** — você verá uma notificação assim:

```json
POST https://webhook.site/unique-id
Headers:
  X-Event-Type: payment.updated
  X-Signature: dGVzdEhtYWM=  ← Assinatura HMAC

Body:
{
  "id": "evt_xyz123",
  "type": "payment.updated",
  "data": {
    "paymentId": "pay_a1b2c3d4",
    "status": "APPROVED",
    "occurredAt": "2025-11-18T14:30:00Z"
  }
}
```

🎉 **Parabéns!** Você processou seu primeiro pagamento completo.

---

## 🏗️ Arquitetura Explicada (Para Quem Nunca Viu)

### Como funciona um gateway de pagamento?

```
┌─────────────┐                  ┌─────────────┐                  ┌─────────────┐
│   Cliente   │                  │   Gateway   │                  │   Lojista   │
│  (Comprador)│                  │  (FiadoPay) │                  │ (Seu site)  │
└─────────────┘                  └─────────────┘                  └─────────────┘
       │                                 │                                 │
       │ 1. POST /payments              │                                 │
       ├────────────────────────────────>│                                 │
       │    {amount: 100, method: CARD}  │                                 │
       │                                 │                                 │
       │ 2. Resposta imediata            │                                 │
       │<────────────────────────────────┤                                 │
       │    {status: PENDING, id: xyz}   │                                 │
       │                                 │                                 │
       │                                 │ 3. Processamento assíncrono     │
       │                                 │    (1.5s delay)                 │
       │                                 │    - Valida cartão              │
       │                                 │    - Verifica fraude            │
       │                                 │    - Aprova/Recusa              │
       │                                 │                                 │
       │                                 │ 4. Webhook (notificação)        │
       │                                 ├────────────────────────────────>│
       │                                 │    POST /webhook                │
       │                                 │    {status: APPROVED}           │
       │                                 │                                 │
       │ 5. Cliente pode consultar       │                                 │
       ├────────────────────────────────>│                                 │
       │    GET /payments/xyz            │                                 │
       │<────────────────────────────────┤                                 │
       │    {status: APPROVED}           │                                 │
```

### Por que essa arquitetura?

1. **Resposta imediata (PENDING):** Não deixa cliente esperando 2-3 segundos
2. **Processamento assíncrono:** Não bloqueia servidor (pode processar milhares simultaneamente)
3. **Webhook:** Lojista recebe notificação automática (não precisa ficar consultando)

---

## 📂 Estrutura do Código (O Que Cada Pasta Faz)

```
src/main/java/edu/ucsal/fiadopay/
│
├── 📁 annotations/           ← Anotações customizadas (magia do Spring!)
│   ├── @PaymentMethod       → Marca classes que processam pagamentos
│   ├── @AntiFraud           → Marca regras de detecção de fraude
│   └── @WebhookSink         → (Reservado para extensões)
│
├── 📁 config/               ← Configurações do Spring
│   ├── AsyncConfig          → Cria thread pools nomeados
│   └── OpenApiConfig        → Configura Swagger (documentação)
│
├── 📁 controller/           ← Endpoints REST (onde chegam as requisições HTTP)
│   ├── AuthController       → POST /auth/token (gera tokens)
│   ├── PaymentController    → POST /payments, GET /payments/{id}
│   ├── MerchantAdminController → POST /admin/merchants
│   └── HealthController     → GET /health (verifica se API está online)
│
├── 📁 domain/               ← Entidades do banco de dados
│   ├── Merchant             → Tabela de lojistas (clientId, secret)
│   ├── Payment              → Tabela de pagamentos (status, valor)
│   └── WebhookDelivery      → Log de webhooks enviados
│
├── 📁 dto/                  ← Objetos de entrada/saída (request/response)
│   ├── request/
│   │   ├── PaymentRequest   → Body do POST /payments
│   │   ├── RefundRequest    → Body do POST /refunds
│   │   └── TokenRequest     → Body do POST /auth/token
│   └── response/
│       ├── PaymentResponse  → Resposta do GET /payments/{id}
│       └── TokenResponse    → Resposta do POST /auth/token
│
├── 📁 handler/              ← Lógica de negócio (processamento)
│   ├── PaymentHandler       → Interface: "Como processar pagamento?"
│   ├── CardPaymentHandler   → Implementa: juros parcelados
│   ├── PixPaymentHandler    → Implementa: sem juros
│   ├── AntiFraudRule        → Interface: "Como detectar fraude?"
│   └── HighAmountFraudRule  → Implementa: valor > R$ 5.000 = fraude
│
├── 📁 registry/             ← Descobre handlers automaticamente (reflexão!)
│   └── PaymentHandlerRegistry → "Qual handler usar para método CARD?"
│
├── 📁 repo/                 ← Acesso ao banco de dados (Spring Data JPA)
│   ├── MerchantRepository
│   ├── PaymentRepository
│   └── WebhookDeliveryRepository
│
├── 📁 service/              ← Orquestração de toda a lógica
│   └── PaymentService       → Une tudo: valida, processa, envia webhook
│
└── FiadoPayApplication.java ← Classe principal (inicia o Spring Boot)
```

### 🔍 Exemplo: Como um pagamento é processado

```
POST /payments
      ↓
PaymentController.create()
      ↓
PaymentService.createPayment()
      ↓
┌────────────────────────────────────────┐
│ 1. Valida autenticação                 │
│ 2. Verifica idempotência (chave única) │
│ 3. Cria entidade Payment (status=PENDING)│
│ 4. Busca handler para método "CARD"   │ ← PaymentHandlerRegistry
│ 5. Aplica regras do handler           │ ← CardPaymentHandler.process()
│ 6. Salva no banco                     │ ← PaymentRepository.save()
│ 7. Agenda processamento assíncrono    │ ← paymentExecutor.execute()
└────────────────────────────────────────┘
      ↓
Retorna resposta {status: PENDING}

[2 segundos depois, em outra thread...]
      ↓
PaymentService.processAsync()
      ↓
┌────────────────────────────────────────┐
│ 1. Aguarda 1.5s (simula delay real)   │
│ 2. Aplica regras antifraude           │ ← Todas as classes @AntiFraud
│ 3. Decide: APPROVED ou DECLINED       │
│ 4. Atualiza status no banco           │
│ 5. Envia webhook para lojista         │ ← webhookExecutor.execute()
└────────────────────────────────────────┘
```

---

## 🧩 Conceitos Avançados Explicados

### 1. **Anotações Customizadas** (@PaymentMethod, @AntiFraud)

**O que são?**  
Marcadores que você coloca em classes para indicar "esta classe tem um propósito especial".

**Exemplo prático:**

```java
// Esta anotação diz: "Sou um processador de pagamento via CARTÃO"
@Service
@PaymentMethod("CARD")  ← Anotação customizada!
public class CardPaymentHandler implements PaymentHandler {
    @Override
    public Payment process(Payment payment, PaymentRequest req) {
        // Calcula juros parcelados
        payment.setTotalWithInterest(req.amount() × 1.01³);
        return payment;
    }
}
```

**Como o sistema descobre isso?**

```java
// PaymentHandlerRegistry.java
@PostConstruct  // Executa ao iniciar a aplicação
public void init() {
    // Busca TODAS as classes com @PaymentMethod
    ctx.getBeansOfType(PaymentHandler.class).forEach(handler -> {
        PaymentMethod ann = handler.getClass().getAnnotation(PaymentMethod.class);
        if (ann != null) {
            handlers.put(ann.value(), handler);  // Armazena: "CARD" → CardPaymentHandler
        }
    });
}
```

**Resultado:** Você pode adicionar `PixPaymentHandler`, `BoletoPaymentHandler` **sem modificar uma linha do registry!**

---

### 2. **Thread Pools (Processamento Paralelo)**

**Problema:** Se processar pagamentos na mesma thread da requisição HTTP:
- Cliente espera 2-3 segundos
- Servidor trava se chegarem 1000 pagamentos simultâneos

**Solução:** Thread pools dedicados

```java
@Bean
public ExecutorService paymentExecutor() {
    return Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()  // Ex: 8 threads em CPU de 8 núcleos
    );
}
```

**Como funciona:**

```
Thread HTTP (Principal)                Thread Pool (Assíncrona)
      │                                      │
      │ 1. Recebe POST /payments             │
      │ 2. Salva com status=PENDING          │
      │ 3. Agenda tarefa no pool ────────────>│
      │ 4. Retorna resposta imediata         │
      │    (cliente não espera!)             │ 1. Aguarda 1.5s
      │                                      │ 2. Valida fraude
      │                                      │ 3. Atualiza status
      │                                      │ 4. Envia webhook
```

---

### 3. **Idempotência (Evita Cobrar 2x)**

**Problema:** Cliente envia pagamento, rede cai, cliente reenvia → cobra 2x!

**Solução:** Chave única (`Idempotency-Key`)

```sql
-- Constraint no banco (Payment.java)
@UniqueConstraint(columnNames = {"merchantId", "idempotencyKey"})

-- Se tentar inserir com mesma chave:
INSERT INTO Payment (..., idempotencyKey='ABC123') → OK (primeira vez)
INSERT INTO Payment (..., idempotencyKey='ABC123') → ERRO (já existe!)
```

```java
// PaymentService.java
if (idempotencyKey != null) {
    var existing = payments.findByIdempotencyKeyAndMerchantId(key, merchantId);
    if (existing.isPresent()) {
        return toResponse(existing.get());  // Retorna pagamento existente
    }
}
```

---

### 4. **Webhooks com Retry Exponencial**

**O que é webhook?**  
Notificação HTTP automática enviada para o lojista quando algo muda.

**Por que retry exponencial?**  
Se servidor do lojista estiver offline, tenta reenviar com intervalos crescentes:

```
Tentativa 1: Falhou → aguarda 1 segundo
Tentativa 2: Falhou → aguarda 2 segundos
Tentativa 3: Falhou → aguarda 3 segundos
Tentativa 4: Falhou → aguarda 4 segundos
Tentativa 5: Falhou → desiste (salva log no banco)
```

**Código:**

```java
private void tryDeliver(Long deliveryId) {
    // ... envia HTTP POST ...
    
    if (!delivered && attempts < 5) {
        Thread.sleep(1000L * attempts);  // Back-off exponencial
        webhookExecutor.execute(() -> tryDeliver(deliveryId));  // Retry
    }
}
```

---

### 5. **HMAC (Assinatura de Webhook)**

**Problema:** Como lojista sabe que webhook veio mesmo do FiadoPay (e não de um hacker)?

**Solução:** Assinatura criptográfica

```java
// FiadoPay gera assinatura
String payload = "{...}";  // JSON do webhook
String secret = "ucsal-2025";  // Segredo compartilhado
String signature = hmac(payload, secret);  // "abc123xyz..."

// Envia no header
X-Signature: abc123xyz...

// Lojista valida
String receivedSignature = request.getHeader("X-Signature");
String calculatedSignature = hmac(request.getBody(), "ucsal-2025");
if (!receivedSignature.equals(calculatedSignature)) {
    throw new Exception("Webhook falsificado!");
}
```

---

## 🧪 Testando Cenários Reais

### Cenário 1: Pagamento com Fraude (Valor Alto)

```bash
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "PIX",
    "currency": "BRL",
    "amount": 6000.00  ← Acima do limite de R$ 5.000
  }'

# Aguarde 2s e consulte
curl http://localhost:8080/fiadopay/gateway/payments/{id}

# ❌ Resultado: status=DECLINED (bloqueado por HighAmountFraudRule)
```

### Cenário 2: Idempotência (Enviar 2x)

```bash
# Primeira requisição
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -H "Idempotency-Key: ORDER-123" \
  -d '{"method":"PIX","amount":50,"currency":"BRL"}'

# Resposta: {"id":"pay_abc","status":"PENDING",...}

# Segunda requisição (MESMA chave)
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -H "Idempotency-Key: ORDER-123" \
  -d '{"method":"PIX","amount":50,"currency":"BRL"}'

# ✅ Resposta: MESMO "id":"pay_abc" (não criou pagamento duplicado!)
```

### Cenário 3: Webhook com Retry

```bash
# 1. Crie merchant com webhook inválido
curl -X POST http://localhost:8080/fiadopay/admin/merchants \
  -d '{"name":"Teste","webhookUrl":"http://localhost:9999/invalid"}'

# 2. Crie pagamento
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -d '{"method":"PIX","amount":10,"currency":"BRL"}'

# 3. Aguarde 10 segundos e verifique logs
# Você verá 5 tentativas falhando com intervalos crescentes

# 4. Consulte banco H2 (http://localhost:8080/h2)
SELECT * FROM WEBHOOK_DELIVERY;
# Verá: attempts=5, delivered=false
```

---

## 🔧 Configurações Avançadas

### Arquivo: `application.yml`

```yaml
fiadopay:
  webhook-secret: ucsal-2025        # Segredo HMAC
  processing-delay-ms: 1500         # Delay no processamento (ms)
  failure-rate: 0.15                # Taxa de recusa (15%)

# Para testar falhas:
# - failure-rate: 1.0  → 100% recusado
# - failure-rate: 0.0  → 100% aprovado
```

### Variáveis de ambiente (production)

```bash
export FIADOPAY_WEBHOOK_SECRET=prod-secret-key
export FIADOPAY_PROCESSING_DELAY=500
export FIADOPAY_FAILURE_RATE=0.05
```

---

## 🐛 Troubleshooting (Problemas Comuns)

### ❌ Erro: "Port 8080 already in use"

**Causa:** Outra aplicação está usando a porta 8080.

**Solução:**

```bash
# Descubra qual processo está usando a porta
lsof -ti:8080  # Linux/Mac
netstat -ano | findstr :8080  # Windows

# Mate o processo OU mude a porta no application.yml:
server:
  port: 8081
```

### ❌ Erro: "Access denied for user 'sa'"

**Causa:** H2 Console configurado incorretamente.

**Solução:**
1. Acesse http://localhost:8080/h2
2. Configure:
    - **JDBC URL:** `jdbc:h2:mem:fiadopay`
    - **User:** `sa`
    - **Password:** (deixe vazio)

### ❌ Webhook não chega

**Causa:** URL não está acessível publicamente.

**Solução:**

```bash
# Use webhook.site para testes
# 1. Acesse https://webhook.site
# 2. Copie URL única (ex: https://webhook.site/abc-123)
# 3. Use ao criar merchant:

curl -X POST http://localhost:8080/fiadopay/admin/merchants \
  -d '{"name":"Teste","webhookUrl":"https://webhook.site/abc-123"}'
```

### ❌ Todos pagamentos são recusados

**Causa:** `failure-rate` muito alto OU regras de fraude muito restritivas.

**Solução:**

```yaml
# Ajuste em application.yml
fiadopay:
  failure-rate: 0.0  # 0% de recusa (100% aprovado)
```

### ❌ "java.lang.OutOfMemoryError"

**Causa:** Thread pools criando muitas threads.

**Solução:**

```bash
# Aumente memória da JVM
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx1024m"
```

---

## 📊 Monitoramento e Debugging

### 1. Ver logs em tempo real

```bash
# No console onde executou mvn spring-boot:run
# Ou configure log em arquivo:

# application.yml
logging:
  file:
    name: logs/fiadopay.log
  level:
    edu.ucsal.fiadopay: DEBUG
```

### 2. H2 Console (Banco de dados)

```
URL: http://localhost:8080/h2
JDBC URL: jdbc:h2:mem:fiadopay
User: sa
Password: (vazio)

Queries úteis:
- SELECT * FROM PAYMENT ORDER BY CREATED_AT DESC;
- SELECT * FROM WEBHOOK_DELIVERY WHERE DELIVERED=FALSE;
- SELECT * FROM MERCHANT;
```

### 3. Swagger UI (Teste interativo)

```
URL: http://localhost:8080/swagger-ui.html

# Clique em "Authorize" e cole token "Bearer FAKE-1"
# Teste endpoints clicando em "Try it out"
```

---

## 🚦 Próximos Passos (Depois de Dominar o Básico)

### Nível 1: Extensões Simples
- [ ] Adicionar método `BOLETO` (vencimento 3 dias)
- [ ] Criar regra de fraude por CPF bloqueado
- [ ] Implementar endpoint `GET /payments` (listar todos)

### Nível 2: Melhorias de Produção
- [ ] Substituir H2 por PostgreSQL
- [ ] Adicionar JWT real (com Spring Security)
- [ ] Implementar rate limiting (Bucket4j)
- [ ] Adicionar testes unitários (JUnit 5)

### Nível 3: Arquitetura Avançada
- [ ] Migrar webhooks para fila (RabbitMQ/SQS)
- [ ] Adicionar Circuit Breaker (Resilience4j)
- [ ] Implementar Event Sourcing (Axon Framework)
- [ ] Deploy em Kubernetes

---

## 📖 Recursos de Aprendizado

### Documentação oficial
- [Spring Boot Guides](https://spring.io/guides)
- [Stripe API Design](https://stripe.com/docs/api) (referência de gateways reais)
- [RFC 7807 - Problem Details](https://datatracker.ietf.org/doc/html/rfc7807)

### Livros recomendados
- "Spring Boot in Action" - Craig Walls
- "Design Patterns" - Gang of Four
- "Release It!" - Michael Nygard (resiliência em produção)

### Cursos
- Alura: "Spring Boot e JPA"
- Udemy: "Master Microservices with Spring Boot"

---

## 🤝 Como Contribuir

### Reportar bugs
1. Verifique se já existe issue similar
2. Abra issue com:
    - Passos para reproduzir
    - Comportamento esperado vs real
    - Logs/screenshots

### Adicionar funcionalidades

```bash
# 1. Fork o projeto
# 2. Crie branch
git checkout -b feature/boleto-handler

# 3. Implemente (exemplo: BoletoPaymentHandler.java)
@Service
@PaymentMethod("BOLETO")
public class BoletoPaymentHandler implements PaymentHandler {
    @Override
    public Payment process(Payment p, PaymentRequest req) {
        p.setTotalWithInterest(req.amount());
        // Lógica de vencimento...
        return p;
    }
}

# 4. Commit e push
git commit -m "feat: adiciona suporte a boleto"
git push origin feature/boleto-handler

# 5. Abra Pull Request
```

---

## 📝 Licença

Projeto educacional para fins acadêmicos (UCSAL 2025).  
