package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.AppUser;
import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.repo.AppUserRepository;
import com.cfs.Drive_Service.repo.DriveFileVersionRepository;
import com.cfs.Drive_Service.repo.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {
    @Mock AppUserRepository users;
    @Mock ItemRepository items;
    @Mock DriveFileVersionRepository versions;
    private StorageQuotaService quota;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach void setUp() {
        quota = new StorageQuotaService(users, items, versions, 100);
        when(users.lockById(owner)).thenReturn(Optional.of(new AppUser("A", "a@example.com", "hash")));
    }

    @Test void allowsWriteThatExactlyFillsQuota() {
        when(items.sumStoredFileBytesByOwnerId(owner, DriveItem.Kind.FILE,
                List.of(DriveItem.Status.READY, DriveItem.Status.TRASHED))).thenReturn(60L);
        when(versions.sumStoredVersionBytesByOwnerId(owner)).thenReturn(30L);
        assertDoesNotThrow(() -> quota.ensureCanAdd(owner, 10));
    }

    @Test void rejectsWriteAboveQuotaWithPayloadTooLarge() {
        when(items.sumStoredFileBytesByOwnerId(owner, DriveItem.Kind.FILE,
                List.of(DriveItem.Status.READY, DriveItem.Status.TRASHED))).thenReturn(60L);
        when(versions.sumStoredVersionBytesByOwnerId(owner)).thenReturn(30L);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> quota.ensureCanAdd(owner, 11));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatusCode());
    }

    @Test void summaryReturnsUsedTotalAndAvailableBytes() {
        when(items.sumStoredFileBytesByOwnerId(owner, DriveItem.Kind.FILE,
                List.of(DriveItem.Status.READY, DriveItem.Status.TRASHED))).thenReturn(60L);
        when(versions.sumStoredVersionBytesByOwnerId(owner)).thenReturn(30L);
        assertEquals(new StorageQuotaService.QuotaSummary(90, 100, 10), quota.summary(owner));
    }
}
