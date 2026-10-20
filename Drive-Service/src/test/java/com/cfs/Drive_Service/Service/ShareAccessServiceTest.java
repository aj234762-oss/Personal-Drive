package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.Model.ShareGrant;
import com.cfs.Drive_Service.repo.ItemRepository;
import com.cfs.Drive_Service.repo.ShareGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareAccessServiceTest {
    @Mock ItemRepository items;
    @Mock ShareGrantRepository grants;
    private ShareAccessService access;
    private DriveItem file;
    private final UUID owner = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();

    @BeforeEach void setUp() {
        access = new ShareAccessService(items, grants);
        file = new DriveItem("notes.txt", null, DriveItem.Kind.FILE, 12, "text/plain", owner);
        file.setStatus(DriveItem.Status.READY);
    }

    @Test void ownerHasOwnerAccessWithoutShareLookup() {
        assertEquals(ShareAccessService.Access.OWNER, access.access(file, owner));
        verifyNoInteractions(grants, items);
    }

    @Test void viewerCanReadButCannotEdit() {
        when(grants.findByItemIdAndRecipientId(file.getId(), recipient))
                .thenReturn(Optional.of(new ShareGrant(file.getId(), recipient, ShareGrant.Permission.VIEWER)));
        assertEquals(ShareAccessService.Access.VIEWER, access.requireRead(file, recipient));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> access.requireEdit(file, recipient));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test void editorCanEdit() {
        when(grants.findByItemIdAndRecipientId(file.getId(), recipient))
                .thenReturn(Optional.of(new ShareGrant(file.getId(), recipient, ShareGrant.Permission.EDITOR)));
        assertEquals(ShareAccessService.Access.EDITOR, access.requireEdit(file, recipient));
    }
}
