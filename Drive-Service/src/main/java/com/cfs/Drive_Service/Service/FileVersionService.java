package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.DriveFileVersion;
import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.client.StorageClient;
import com.cfs.Drive_Service.repo.DriveFileVersionRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileVersionService {
    private final DriveFileVersionRepository versions;
    private final ItemService items;
    private final StorageClient storage;
    private final StorageQuotaService quota;

    public FileVersionService(DriveFileVersionRepository versions, ItemService items, StorageClient storage, StorageQuotaService quota) {
        this.versions = versions;
        this.items = items;
        this.storage = storage;
        this.quota = quota;
    }

    @Transactional
    public List<DriveFileVersion> history(UUID itemId, UUID actorId) {
        DriveItem item = requireFile(items.get(itemId, actorId));
        return versions.findByItemIdAndOwnerIdOrderByRevisionNumberDesc(item.getId(), item.getOwnerId());
    }

    @Transactional
    public DriveItem restore(UUID itemId, UUID versionId, UUID actorId) {
        DriveItem item = requireFile(items.getForEdit(itemId, actorId));
        DriveFileVersion selected = versions.findByIdAndItemIdAndOwnerId(versionId, itemId, item.getOwnerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File version not found"));

        quota.ensureCanAdd(item.getOwnerId(), selected.getSize());
        snapshot(item, actorId);
        storage.copy(selected.getObjectId(), item.getId());
        return items.edit(itemId, actorId, selected.getName(), selected.getSize(), selected.getMediaType());
    }

    @Transactional
    public void snapshotBeforeReplace(DriveItem item, UUID actorId) {
        requireFile(item);
        snapshot(item, actorId);
    }

    @Transactional
    public void deleteForItem(UUID itemId) {
        List<DriveFileVersion> oldVersions = versions.findAllByItemIdOrderByRevisionNumberDesc(itemId);
        for (DriveFileVersion version : oldVersions) storage.delete(version.getObjectId());
        versions.deleteAll(oldVersions);
    }

    private void snapshot(DriveItem item, UUID actorId) {
        UUID objectId = UUID.randomUUID();
        try {
            storage.copy(item.getId(), objectId);
            long revision = versions.countByItemIdAndOwnerId(item.getId(), item.getOwnerId()) + 1;
            versions.saveAndFlush(new DriveFileVersion(item.getId(), item.getOwnerId(), objectId, revision,
                    item.getName(), item.getSize(), item.getMediaType(), actorId));
        } catch (RuntimeException failure) {
            try { storage.delete(objectId); }
            catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    private static DriveItem requireFile(DriveItem item) {
        if (item.getKind() != DriveItem.Kind.FILE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version history is only available for files");
        return item;
    }
}
