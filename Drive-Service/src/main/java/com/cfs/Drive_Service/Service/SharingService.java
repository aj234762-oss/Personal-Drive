package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.AppUser;
import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.Model.ShareGrant;
import com.cfs.Drive_Service.repo.AppUserRepository;
import com.cfs.Drive_Service.repo.ItemRepository;
import com.cfs.Drive_Service.repo.ShareGrantRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SharingService {
    private final ItemRepository items;
    private final AppUserRepository users;
    private final ShareGrantRepository grants;
    private final ShareAccessService access;

    public SharingService(ItemRepository items, AppUserRepository users, ShareGrantRepository grants, ShareAccessService access) {
        this.items = items; this.users = users; this.grants = grants; this.access = access;
    }

    @Transactional
    public ShareRecord share(UUID itemId, UUID ownerId, String email, ShareGrant.Permission permission) {
        DriveItem item = ownedItem(itemId, ownerId);
        if (item.getStatus() != DriveItem.Status.READY) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only available items can be shared");
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        AppUser recipient = users.findByEmail(normalized).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No Drive account exists for that email"));
        if (recipient.getId().equals(ownerId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You already own this item");
        ShareGrant grant = grants.findByItemIdAndRecipientId(itemId, recipient.getId()).orElseGet(() -> new ShareGrant(itemId, recipient.getId(), permission));
        grant.setPermission(permission);
        grants.save(grant);
        return new ShareRecord(recipient.getId(), recipient.getName(), recipient.getEmail(), grant.getPermission().name(), grant.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ShareRecord> listShares(UUID itemId, UUID ownerId) {
        ownedItem(itemId, ownerId);
        return grants.findByItemIdOrderByCreatedAtAsc(itemId).stream().map(grant -> users.findById(grant.getRecipientId())
                .map(user -> new ShareRecord(user.getId(), user.getName(), user.getEmail(), grant.getPermission().name(), grant.getCreatedAt()))
                .orElse(null)).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional
    public void revoke(UUID itemId, UUID ownerId, UUID recipientId) {
        ownedItem(itemId, ownerId);
        grants.deleteByItemIdAndRecipientId(itemId, recipientId);
    }

    @Transactional(readOnly = true)
    public Page<DriveItem> sharedWithMe(UUID recipientId, Pageable pageable) {
        List<DriveItem> visible = visibleDirectShares(recipientId);
        int start = (int) Math.min(pageable.getOffset(), visible.size());
        int end = Math.min(start + pageable.getPageSize(), visible.size());
        List<DriveItem> slice = visible.subList(start, end);
        decorateSharedItems(slice, recipientId);
        return new PageImpl<>(slice, pageable, visible.size());
    }

    @Transactional(readOnly = true)
    public List<SharerGroup> sharers(UUID recipientId) {
        Map<UUID, Set<UUID>> sharedItemIds = new LinkedHashMap<>();
        for (DriveItem item : visibleDirectShares(recipientId))
            sharedItemIds.computeIfAbsent(item.getOwnerId(), ignored -> new java.util.HashSet<>()).add(item.getId());
        List<SharerGroup> result = new ArrayList<>();
        sharedItemIds.forEach((ownerId, ids) -> users.findById(ownerId).ifPresent(owner ->
                result.add(new SharerGroup(owner.getId(), owner.getName(), owner.getEmail(), ids.size()))));
        result.sort(Comparator.comparing(SharerGroup::email, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional(readOnly = true)
    public Page<DriveItem> sharedByOwner(UUID recipientId, UUID ownerId, String query, String sort, org.springframework.data.domain.Sort.Direction direction, int page, int size) {
        List<DriveItem> visible = visibleDirectShares(recipientId).stream().filter(item -> ownerId.equals(item.getOwnerId())).collect(Collectors.toCollection(ArrayList::new));
        if (visible.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No items from this person are shared with you");
        if (query != null && !query.isBlank()) {
            String needle = query.trim().toLowerCase(Locale.ROOT);
            visible.removeIf(item -> !item.getName().toLowerCase(Locale.ROOT).contains(needle));
        }
        Comparator<DriveItem> comparator = switch (sort) {
            case "createdAt" -> Comparator.comparing(DriveItem::getCreatedAt);
            case "size" -> Comparator.comparingLong(DriveItem::getSize);
            default -> Comparator.comparing(DriveItem::getName, String.CASE_INSENSITIVE_ORDER);
        };
        if (direction == org.springframework.data.domain.Sort.Direction.DESC) comparator = comparator.reversed();
        visible.sort(comparator.thenComparing(DriveItem::getId));
        int start = (int) Math.min((long) page * size, visible.size());
        int end = Math.min(start + size, visible.size());
        List<DriveItem> slice = visible.subList(start, end);
        decorateSharedItems(slice, recipientId);
        return new PageImpl<>(slice, PageRequest.of(page, size), visible.size());
    }

    private List<DriveItem> visibleDirectShares(UUID recipientId) {
        return grants.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .map(grant -> items.findById(grant.getItemId()).orElse(null))
                .filter(item -> item != null && item.getOwnerId() != null && item.getStatus() == DriveItem.Status.READY && access.access(item, recipientId) != ShareAccessService.Access.NONE)
                .distinct().toList();
    }

    private void decorateSharedItems(List<DriveItem> slice, UUID recipientId) {
        slice.forEach(item -> {
            item.setAccessLevel(access.access(item, recipientId).name());
            users.findById(item.getOwnerId()).ifPresent(owner -> item.setSharedOwnerEmail(owner.getEmail()));
        });
    }

    private DriveItem ownedItem(UUID itemId, UUID ownerId) {
        DriveItem item = items.findById(itemId).filter(candidate -> ownerId.equals(candidate.getOwnerId())).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        return item;
    }

    public record ShareRecord(UUID userId, String name, String email, String permission, java.time.Instant createdAt) {}
    public record SharerGroup(UUID id, String name, String email, long itemCount) {}
}
