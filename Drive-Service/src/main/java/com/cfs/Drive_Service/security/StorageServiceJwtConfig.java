package com.cfs.Drive_Service.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class StorageServiceJwtConfig {
    @Bean("storageJwtEncoder")
    JwtEncoder storageJwtEncoder(@Value("${drive.storage.jwt-secret}") String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalStateException("Drive-to-Storage JWT secret must be at least 32 bytes");
        SecretKey signingKey = new SecretKeySpec(key, "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
    }
}
