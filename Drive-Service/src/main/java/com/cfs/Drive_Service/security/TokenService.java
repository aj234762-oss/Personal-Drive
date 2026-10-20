package com.cfs.Drive_Service.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private static final Duration LIFETIME = Duration.ofMinutes(15);
    private final JwtEncoder encoder;

    public TokenService(@Qualifier("jwtEncoder") JwtEncoder encoder) { this.encoder = encoder; }

    public TokenResult create(Authentication authentication) {
        DriveUserPrincipal user = (DriveUserPrincipal) authentication.getPrincipal();
        return createFor(user);
    }

    public TokenResult createFor(DriveUserPrincipal user) {
        Instant now = Instant.now();
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).map(role -> role.replaceFirst("^ROLE_", "")).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("drive-local")
                .subject(user.id().toString()).id(java.util.UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plus(LIFETIME))
                .claim("email", user.email()).claim("roles", roles).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResult(token, user.id().toString(), user.email(), LIFETIME.toSeconds());
    }

    public record TokenResult(String accessToken, String userId, String email, long expiresIn) {}
}
