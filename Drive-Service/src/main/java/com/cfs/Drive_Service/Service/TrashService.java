package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.client.StorageClient;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TrashService {
    private final ItemService items;
    private final StorageClient storage;
    private final FileVersionService fileVersions;

    public TrashService(ItemService items, StorageClient storage, FileVersionService fileVersions) {
        this.items = items;
        this.storage = storage;
        this.fileVersions = fileVersions;
    }

    public List<DriveItem> list(UUID ownerId) {
        return items.trashRoots(ownerId);
    }

    public DriveItem restore(UUID id, UUID ownerId) {
        return items.restore(id, ownerId);
    }

    public void permanentlyDelete(UUID id, UUID ownerId) {
        List<DriveItem> subtree = items.trashedSubtreePostOrder(id, ownerId);
        for (DriveItem item : subtree) {
            if (item.getKind() == DriveItem.Kind.FILE) {
                fileVersions.deleteForItem(item.getId());
                storage.delete(item.getId());
            }
        }
        for (DriveItem item : subtree) items.remove(item.getId());
    }
}
