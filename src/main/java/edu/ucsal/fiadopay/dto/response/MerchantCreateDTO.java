package edu.ucsal.fiadopay.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantCreateDTO(
    @NotBlank @Size(max = 120) String name,
    @NotBlank String webhookUrl
) {}
