package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.DriveFileVersion;
import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.client.StorageClient;
import com.cfs.Drive_Service.repo.DriveFileVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileVersionServiceTest {
    @Mock DriveFileVersionRepository versions;
    @Mock ItemService items;
    @Mock StorageClient storage;
    @Mock StorageQuotaService quota;
    private FileVersionService service;
    private DriveItem item;
    private final UUID owner = UUID.randomUUID();
    private final UUID editor = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();

    @BeforeEach void setUp() {
        service = new FileVersionService(versions, items, storage, quota);
        item = new DriveItem("report.txt", null, DriveItem.Kind.FILE, 4, "text/plain", owner);
        item.setStatus(DriveItem.Status.READY);
    }

    @Test void replacementSnapshotKeepsOriginalMetadataAndOwner() {
        when(versions.countByItemIdAndOwnerId(item.getId(), owner)).thenReturn(0L);
        service.snapshotBeforeReplace(item, editor);
        ArgumentCaptor<DriveFileVersion> saved = ArgumentCaptor.forClass(DriveFileVersion.class);
        verify(storage).copy(eq(item.getId()), any(UUID.class));
        verify(versions).saveAndFlush(saved.capture());
        assertEquals(item.getId(), saved.getValue().getItemId());
        assertEquals(owner, saved.getValue().getOwnerId());
        assertEquals("report.txt", saved.getValue().getName());
        assertEquals(4, saved.getValue().getSize());
        assertEquals("text/plain", saved.getValue().getMediaType());
        assertEquals(editor, saved.getValue().getCreatedBy());
    }

    @Test void restoreSnapshotsCurrentContentThenCopiesSelectedVersion() {
        UUID versionId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        DriveFileVersion selected = new DriveFileVersion(item.getId(), owner, objectId, 1, "old.txt", 9, "text/plain", owner);
        when(items.getForEdit(item.getId(), editor)).thenReturn(item);
        when(versions.findByIdAndItemIdAndOwnerId(versionId, item.getId(), owner)).thenReturn(Optional.of(selected));
        when(versions.countByItemIdAndOwnerId(item.getId(), owner)).thenReturn(1L);
        when(items.edit(item.getId(), editor, "old.txt", 9L, "text/plain")).thenReturn(item);
        assertSame(item, service.restore(item.getId(), versionId, editor));
        verify(storage).copy(eq(item.getId()), any(UUID.class));
        verify(storage).copy(objectId, item.getId());
        verify(versions).saveAndFlush(any(DriveFileVersion.class));
        verify(items).edit(item.getId(), editor, "old.txt", 9L, "text/plain");
    }

    @Test void viewerCannotRestoreVersion() {
        when(items.getForEdit(item.getId(), viewer)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class, () -> service.restore(item.getId(), UUID.randomUUID(), viewer));
        verifyNoInteractions(versions, storage);
    }
}
