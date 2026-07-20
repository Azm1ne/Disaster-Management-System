package bd.dms.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank String refreshToken) {}
