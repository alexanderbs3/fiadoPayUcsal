package edu.ucsal.fiadopay.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefundRequest(
    @NotBlank String paymentId
) {}
