package cn.cctstudio.smartdiypcassistantbackend.security;

import cn.cctstudio.smartdiypcassistantbackend.config.properties.SecurityProperties;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecurityProperties properties;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
    }

    public String createToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(properties.jwtExpirationMinutes() * 60);
        return Jwts.builder()
                .subject(String.valueOf(principal.userId()))
                .claim("username", principal.username())
                .claim("role", principal.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey())
                .compact();
    }

    public UserPrincipal parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new UserPrincipal(
                Long.parseLong(claims.getSubject()),
                claims.get("username", String.class),
                UserRole.valueOf(claims.get("role", String.class))
        );
    }

    private SecretKey secretKey() {
        byte[] keyBytes = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
