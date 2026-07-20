package bd.dms.auth;

import bd.dms.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies stateless access tokens (JWT, HMAC-SHA256). The {@code role} claim
 * is authoritative for authorization, so a request is authorized from the token alone
 * without a database lookup.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(
            @Value("${dms.jwt.secret:dev-secret-change-me-please-at-least-32-bytes-long}") String secret,
            @Value("${dms.jwt.access-ttl:PT15M}") Duration accessTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
    }

    public String issueAccessToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("name_en", user.getNameEn())
                .claim("name_bn", user.getNameBn())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Verifies signature and expiry; throws {@link io.jsonwebtoken.JwtException} if invalid. */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }
}
