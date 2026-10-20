package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.Model.ShareGrant;
import com.cfs.Drive_Service.repo.ItemRepository;
import com.cfs.Drive_Service.repo.ShareGrantRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShareAccessService {
    public enum Access { NONE, VIEWER, EDITOR, OWNER }
    private final ItemRepository items;
    private final ShareGrantRepository grants;

    public ShareAccessService(ItemRepository items, ShareGrantRepository grants) { this.items = items; this.grants = grants; }

    public Access access(DriveItem item, UUID actorId) {
        if (actorId.equals(item.getOwnerId())) return Access.OWNER;
        if (item.getStatus() != DriveItem.Status.READY) return Access.NONE;
        Set<UUID> visited = new HashSet<>();
        DriveItem cursor = item;
        boolean direct = true;
        while (cursor != null && visited.add(cursor.getId())) {
            if (cursor.getStatus() != DriveItem.Status.READY) return Access.NONE;
            if (actorId.equals(cursor.getOwnerId())) return Access.OWNER;
            var grant = grants.findByItemIdAndRecipientId(cursor.getId(), actorId);
            if (grant.isPresent()) return grant.get().getPermission() == ShareGrant.Permission.EDITOR ? Access.EDITOR : Access.VIEWER;
            if (direct || cursor.getKind() == DriveItem.Kind.FOLDER) {
                cursor = cursor.getParentId() == null ? null : items.findById(cursor.getParentId()).orElse(null);
                direct = false;
            } else break;
        }
        return Access.NONE;
    }

    public Access requireRead(DriveItem item, UUID actorId) {
        Access access = access(item, actorId);
        if (access == Access.NONE) throw missing();
        return access;
    }
    public Access requireEdit(DriveItem item, UUID actorId) {
        Access access = requireRead(item, actorId);
        if (access != Access.OWNER && access != Access.EDITOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have viewer access to this item");
        return access;
    }
    public void requireOwner(DriveItem item, UUID actorId) {
        if (!actorId.equals(item.getOwnerId())) throw missing();
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"); }
}
