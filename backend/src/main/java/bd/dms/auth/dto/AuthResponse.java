package bd.dms.auth.dto;

/**
 * The result of a successful login or refresh: a short-lived access token to send on every
 * request, a long-lived refresh token to obtain the next access token, and enough identity
 * (role + bilingual display name) for the SPA to route and greet the user without a second call.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        String role,
        String username,
        String nameEn,
        String nameBn) {}
