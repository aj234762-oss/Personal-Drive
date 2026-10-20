package com.cfs.Drive_Service.client;

import feign.RequestInterceptor;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

public class StorageClientAuthConfiguration {
    @Bean
    RequestInterceptor storageAuthHeader(@Qualifier("storageJwtEncoder") JwtEncoder encoder) {
        return request -> {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder().issuer("drive-service").subject("drive-service")
                    .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("roles", List.of("STORAGE_CLIENT")).build();
            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
            request.header("Authorization", "Bearer " + token);
        };
    }
}
