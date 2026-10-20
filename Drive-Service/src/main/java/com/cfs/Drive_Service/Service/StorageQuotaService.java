package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.repo.AppUserRepository;
import com.cfs.Drive_Service.repo.DriveFileVersionRepository;
import com.cfs.Drive_Service.repo.ItemRepository;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StorageQuotaService {
    private final AppUserRepository users;
    private final ItemRepository items;
    private final DriveFileVersionRepository versions;
    private final long quotaBytes;

    public StorageQuotaService(AppUserRepository users, ItemRepository items, DriveFileVersionRepository versions,
                               @Value("${drive.storage.quota-bytes:5368709120}") long quotaBytes) {
        if (quotaBytes < 1) throw new IllegalArgumentException("Storage quota must be positive");
        this.users = users;
        this.items = items;
        this.versions = versions;
        this.quotaBytes = quotaBytes;
    }

    @Transactional
    public QuotaSummary summary(UUID ownerId) {
        requireUser(ownerId);
        long used = usedBytes(ownerId);
        return new QuotaSummary(used, quotaBytes, Math.max(0, quotaBytes - used));
    }

    /** Locks the account row so concurrent writes cannot both spend the same remaining quota. */
    @Transactional
    public void ensureCanAdd(UUID ownerId, long additionalBytes) {
        if (additionalBytes < 0) throw new IllegalArgumentException("Additional storage cannot be negative");
        requireUser(ownerId);
        long used = usedBytes(ownerId);
        if (additionalBytes > quotaBytes - Math.min(used, quotaBytes)) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Storage quota exceeded. " + formatBytes(Math.max(0, quotaBytes - used)) + " available.");
        }
    }

    private void requireUser(UUID ownerId) {
        users.lockById(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private long usedBytes(UUID ownerId) {
        return Math.addExact(items.sumStoredFileBytesByOwnerId(ownerId, com.cfs.Drive_Service.Model.DriveItem.Kind.FILE,
                        java.util.List.of(com.cfs.Drive_Service.Model.DriveItem.Status.READY, com.cfs.Drive_Service.Model.DriveItem.Status.TRASHED)),
                versions.sumStoredVersionBytesByOwnerId(ownerId));
    }

    private static String formatBytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024 * 1024));
    }

    public record QuotaSummary(long usedBytes, long quotaBytes, long availableBytes) {}
}
