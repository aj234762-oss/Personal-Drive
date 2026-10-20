package com.cfs.Drive_Service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Issues short-lived, file-scoped read tickets for browser media elements. */
@Service
public class PreviewTicketService {
    private static final long LIFETIME_SECONDS = 300;
    private final byte[] secret;

    public PreviewTicketService(@Value("${drive.security.preview-key-file:./data/preview-secret.key}") String configuredPath) throws IOException {
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        if (Files.notExists(path)) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            try { Files.write(path, random, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
            catch (java.nio.file.FileAlreadyExistsException ignored) { }
            try { Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException ignored) { }
        }
        secret = Files.readAllBytes(path);
        if (secret.length < 32) throw new IllegalStateException("Preview signing key must contain at least 256 bits");
    }

    public Ticket issue(UUID itemId, UUID ownerId) {
        long expiresAt = Instant.now().getEpochSecond() + LIFETIME_SECONDS;
        String payload = itemId + "." + ownerId + "." + expiresAt + "." + UUID.randomUUID();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return new Ticket(encoded + "." + sign(encoded), LIFETIME_SECONDS);
    }

    public UUID validate(String token, UUID expectedItemId) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII)))
                throw invalid();
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] claims = payload.split("\\.", -1);
            if (claims.length != 4 || !UUID.fromString(claims[0]).equals(expectedItemId) || Long.parseLong(claims[2]) < Instant.now().getEpochSecond())
                throw invalid();
            return UUID.fromString(claims[1]);
        } catch (ResponseStatusException exception) { throw exception; }
        catch (RuntimeException exception) { throw invalid(); }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException("Could not sign preview ticket", exception); }
    }

    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Preview link is invalid or expired"); }
    public record Ticket(String token, long expiresIn) { }
}
