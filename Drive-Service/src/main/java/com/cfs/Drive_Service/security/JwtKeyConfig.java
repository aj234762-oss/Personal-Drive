package com.cfs.Drive_Service.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtKeyConfig {
    @Bean
    SecretKey jwtSecretKey(@Value("${drive.security.jwt-key-file:./data/jwt-secret.key}") String configuredPath) throws IOException {
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        if (Files.notExists(path)) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String encoded = Base64.getEncoder().encodeToString(random);
            try {
                Files.writeString(path, encoded, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Another local process created the key first.
            }
            try {
                Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Keep the platform's default file permissions where POSIX permissions are unavailable.
            }
        }
        byte[] key = Base64.getDecoder().decode(Files.readString(path, StandardCharsets.US_ASCII).trim());
        if (key.length < 32) throw new IllegalStateException("JWT key file must contain at least 256 bits");
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("drive-local"));
        return decoder;
    }
}
