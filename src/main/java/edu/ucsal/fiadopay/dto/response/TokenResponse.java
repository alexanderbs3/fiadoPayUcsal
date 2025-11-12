package edu.ucsal.fiadopay.dto.response;
public record TokenResponse(String access_token, String token_type, long expires_in) {}
