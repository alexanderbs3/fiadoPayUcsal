package edu.ucsal.fiadopay.dto.response;
import java.math.BigDecimal;
public record PaymentResponse(String id, String status, String method, BigDecimal amount, Integer installments, Double interestRate, BigDecimal total) {}
