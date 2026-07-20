package bd.dms.auth;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final Duration refreshTtl;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
            UserRepository users, RefreshTokenRepository refreshTokens,
            @Value("${dms.jwt.refresh-ttl:P7D}") Duration refreshTtl) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.refreshTtl = refreshTtl;
    }

    /** Verifies credentials and issues a fresh token pair. */
    @Transactional
    public AuthResponse login(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Unknown user"));
        return issue(user);
    }

    /** Rotates a valid refresh token: the presented token is revoked and a new pair issued. */
    @Transactional
    public AuthResponse refresh(String token) {
        RefreshToken current = refreshTokens.findByToken(token)
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        current.revoke();
        return issue(current.getUser());
    }

    /** Ends the session by revoking the refresh token; unknown tokens are ignored. */
    @Transactional
    public void logout(String token) {
        refreshTokens.findByToken(token).ifPresent(RefreshToken::revoke);
    }

    private AuthResponse issue(AppUser user) {
        String accessToken = jwtService.issueAccessToken(user);
        RefreshToken refreshToken = refreshTokens.save(new RefreshToken(
                UUID.randomUUID().toString(), user, Instant.now().plus(refreshTtl)));
        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                "Bearer",
                jwtService.getAccessTtl().toSeconds(),
                user.getRole().name(),
                user.getUsername(),
                user.getNameEn(),
                user.getNameBn());
    }
}
