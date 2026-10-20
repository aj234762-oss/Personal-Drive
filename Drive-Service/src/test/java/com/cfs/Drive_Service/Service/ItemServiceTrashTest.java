package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.repo.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTrashTest {
    @Mock ItemRepository items;
    @Mock ShareAccessService sharing;
    private ItemService service;
    private DriveItem file;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach void setUp() {
        service = new ItemService(items, sharing);
        file = new DriveItem("notes.txt", null, DriveItem.Kind.FILE, 12, "text/plain", owner);
        file.setStatus(DriveItem.Status.READY);
    }

    @Test void trashMarksFileAndRestoreMakesItAvailableAgain() {
        when(items.lockByIdAndOwnerId(file.getId(), owner)).thenReturn(Optional.of(file));
        when(items.findByOwnerIdAndParentIdOrderByNameAsc(owner, file.getId())).thenReturn(List.of());

        DriveItem trashed = service.trash(file.getId(), owner);
        assertEquals(DriveItem.Status.TRASHED, trashed.getStatus());
        assertNotNull(trashed.getTrashedAt());

        DriveItem restored = service.restore(file.getId(), owner);
        assertSame(file, restored);
        assertEquals(DriveItem.Status.READY, restored.getStatus());
        assertNull(restored.getTrashedAt());
    }
}
