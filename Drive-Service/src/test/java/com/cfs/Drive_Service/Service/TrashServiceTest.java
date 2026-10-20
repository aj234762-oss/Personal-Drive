package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.client.StorageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrashServiceTest {
    @Mock ItemService items;
    @Mock StorageClient storage;
    @Mock FileVersionService versions;

    @Test void permanentDeleteRemovesVersionsAndStoredContentBeforeMetadata() {
        UUID owner = UUID.randomUUID();
        DriveItem file = new DriveItem("notes.txt", null, DriveItem.Kind.FILE, 12, "text/plain", owner);
        file.setStatus(DriveItem.Status.TRASHED);
        when(items.trashedSubtreePostOrder(file.getId(), owner)).thenReturn(List.of(file));
        TrashService trash = new TrashService(items, storage, versions);

        trash.permanentlyDelete(file.getId(), owner);

        var order = inOrder(versions, storage, items);
        order.verify(versions).deleteForItem(file.getId());
        order.verify(storage).delete(file.getId());
        order.verify(items).remove(file.getId());
    }
}
