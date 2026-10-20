package com.cfs.Drive_Service.security;

import com.cfs.Drive_Service.Model.AppUser;
import com.cfs.Drive_Service.Model.RefreshSession;
import com.cfs.Drive_Service.repo.AppUserRepository;
import com.cfs.Drive_Service.repo.RefreshSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshSessionService {
    private static final Duration LIFETIME = Duration.ofDays(7);
    private final RefreshSessionRepository sessions;
    private final AppUserRepository users;
    private final SecureRandom random = new SecureRandom();

    public RefreshSessionService(RefreshSessionRepository sessions, AppUserRepository users) {
        this.sessions = sessions;
        this.users = users;
    }

    @Transactional
    public String issue(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> unauthorized());
        String rawToken = newToken();
        Instant now = Instant.now();
        sessions.save(new RefreshSession(user, hash(rawToken), now.plus(LIFETIME), now));
        return rawToken;
    }

    @Transactional
    public RotatedSession rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw unauthorized();
        RefreshSession old = sessions.findByTokenHashForUpdate(hash(rawToken)).orElseThrow(RefreshSessionService::unauthorized);
        Instant now = Instant.now();
        if (old.getRevokedAt() != null || !old.getExpiresAt().isAfter(now)) throw unauthorized();
        AppUser user = old.getUser();
        old.revoke(now);
        String nextToken = newToken();
        sessions.save(new RefreshSession(user, hash(nextToken), now.plus(LIFETIME), now));
        return new RotatedSession(new DriveUserPrincipal(user), nextToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        sessions.findByTokenHashForUpdate(hash(rawToken)).ifPresent(session -> {
            if (session.getRevokedAt() == null) session.revoke(Instant.now());
        });
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again to continue");
    }

    public record RotatedSession(DriveUserPrincipal principal, String refreshToken) {}
}
