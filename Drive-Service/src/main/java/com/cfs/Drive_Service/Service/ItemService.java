package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.repo.ItemRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemService {
    private final ItemRepository itemRepository;
    private final ShareAccessService sharing;
    public ItemService(ItemRepository itemRepository, ShareAccessService sharing) { this.itemRepository = itemRepository; this.sharing = sharing; }

    @Transactional
    public DriveItem create(String name, UUID parentId, DriveItem.Kind kind, long size, String mediaType, UUID ownerId) {
        if (name == null || name.isBlank() || name.length() > 255 || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid item name");
        }
        if (parentId != null) {
            DriveItem parent = get(parentId, ownerId);
            sharing.requireEdit(parent, ownerId);
            if (parent.getKind() != DriveItem.Kind.FOLDER || parent.getStatus() != DriveItem.Status.READY) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Parent must be an available folder");
            }
        }
        return itemRepository.saveAndFlush(new DriveItem(name, parentId, kind, size, mediaType, ownerId));
    }

    public Page<DriveItem> list(UUID parentId, UUID ownerId, String query, String sortBy,
                                Sort.Direction direction, int page, int size) {
        DriveItem parent = null;
        if (parentId != null) {
            parent = get(parentId, ownerId);
            sharing.requireRead(parent, ownerId);
            if (parent.getKind() != DriveItem.Kind.FOLDER) throw new ResponseStatusException(HttpStatus.CONFLICT, "Parent must be a folder");
        }
        Sort sort = Sort.by(new Sort.Order(direction, sortBy));
        if ("name".equals(sortBy)) sort = Sort.by(new Sort.Order(direction, sortBy).ignoreCase());
        sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim().toLowerCase(java.util.Locale.ROOT);
        Page<DriveItem> result = parent == null
                ? itemRepository.searchVisibleItems(ownerId, null, DriveItem.Status.READY, normalizedQuery, pageable)
                : itemRepository.searchChildren(parentId, DriveItem.Status.READY, normalizedQuery, pageable);
        result.getContent().forEach(item -> item.setAccessLevel(sharing.access(item, ownerId).name()));
        return result;
    }

    @Transactional
    public DriveItem get(UUID id, UUID ownerId) {
        DriveItem item = itemRepository.findById(id).orElseThrow(ItemService::missing);
        if (item.getStatus() != DriveItem.Status.READY) throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is not ready");
        sharing.requireRead(item, ownerId);
        return item;
    }

    public DriveItem getForEdit(UUID id, UUID actorId) {
        DriveItem item = get(id, actorId);
        sharing.requireEdit(item, actorId);
        return item;
    }

    @Transactional
    public DriveItem ready(UUID id, UUID ownerId) {
        DriveItem item = itemRepository.lockByIdAndOwnerId(id, ownerId).orElseThrow(ItemService::missing);
        if (item.getStatus() != DriveItem.Status.PENDING) throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is no longer pending");
        item.setStatus(DriveItem.Status.READY);
        return item;
    }

    @Transactional
    public DriveItem edit(UUID id, UUID actorId, String name, Long replacementSize, String replacementMediaType) {
        DriveItem item = itemRepository.findById(id).orElseThrow(ItemService::missing);
        if (item.getStatus() != DriveItem.Status.READY) throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is not available to edit");
        sharing.requireEdit(item, actorId);
        validateName(name);
        item.setName(name.trim());
        if (replacementSize != null) {
            if (item.getKind() != DriveItem.Kind.FILE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder contents cannot be replaced");
            item.setSize(replacementSize);
            item.setMediaType(replacementMediaType);
        }
        return itemRepository.saveAndFlush(item);
    }

    public static void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 255 || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid item name");
        }
    }

    @Transactional
    public DriveItem trash(UUID id, UUID ownerId) {
        DriveItem item = itemRepository.lockByIdAndOwnerId(id, ownerId).orElseThrow(ItemService::missing);
        if (item.getStatus() == DriveItem.Status.PENDING) throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is still pending");
        if (item.getStatus() != DriveItem.Status.READY) throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is not available to move to Trash");
        List<DriveItem> subtree = new ArrayList<>();
        collectSubtree(item, ownerId, subtree, new HashSet<>());
        if (subtree.stream().anyMatch(child -> child.getStatus() == DriveItem.Status.PENDING || child.getStatus() == DriveItem.Status.DELETING))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Folder contains an item that is still being processed");
        Instant now = Instant.now();
        for (DriveItem child : subtree) {
            child.setStatus(DriveItem.Status.TRASHED);
            child.setTrashedAt(now);
        }
        return item;
    }

    public List<DriveItem> trashRoots(UUID ownerId) {
        List<DriveItem> trashed = itemRepository.findByOwnerIdAndStatusOrderByNameAsc(ownerId, DriveItem.Status.TRASHED);
        Set<UUID> trashedIds = trashed.stream().map(DriveItem::getId).collect(Collectors.toSet());
        return trashed.stream().filter(item -> item.getParentId() == null || !trashedIds.contains(item.getParentId())).toList();
    }

    @Transactional
    public DriveItem restore(UUID id, UUID ownerId) {
        DriveItem item = itemRepository.lockByIdAndOwnerId(id, ownerId).orElseThrow(ItemService::missing);
        if (item.getStatus() != DriveItem.Status.TRASHED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is not in Trash");
        if (item.getParentId() != null) {
            DriveItem parent = itemRepository.findById(item.getParentId()).filter(candidate -> ownerId.equals(candidate.getOwnerId())).orElseThrow(ItemService::missing);
            if (parent.getStatus() != DriveItem.Status.READY)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Restore the parent folder first");
        }
        List<DriveItem> subtree = new ArrayList<>();
        collectSubtree(item, ownerId, subtree, new HashSet<>());
        for (DriveItem child : subtree) {
            if (child.getStatus() == DriveItem.Status.TRASHED) {
                child.setStatus(DriveItem.Status.READY);
                child.setTrashedAt(null);
            }
        }
        return item;
    }

    @Transactional
    public List<DriveItem> trashedSubtreePostOrder(UUID id, UUID ownerId) {
        DriveItem root = itemRepository.findById(id).filter(candidate -> ownerId.equals(candidate.getOwnerId())).orElseThrow(ItemService::missing);
        if (root.getStatus() != DriveItem.Status.TRASHED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is not in Trash");
        if (root.getParentId() != null) {
            DriveItem parent = itemRepository.findById(root.getParentId()).filter(candidate -> ownerId.equals(candidate.getOwnerId())).orElseThrow(ItemService::missing);
            if (parent.getStatus() == DriveItem.Status.TRASHED)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Delete the item from its Trash folder");
        }
        List<DriveItem> subtree = new ArrayList<>();
        collectSubtree(root, ownerId, subtree, new HashSet<>());
        if (subtree.stream().anyMatch(item -> item.getStatus() != DriveItem.Status.TRASHED))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The folder contains an item that is not in Trash");
        return subtree;
    }

    @Transactional
    public void remove(UUID id) { itemRepository.deleteById(id); }

    private void collectSubtree(DriveItem item, UUID ownerId, List<DriveItem> output, Set<UUID> visited) {
        if (!visited.add(item.getId())) return;
        for (DriveItem child : itemRepository.findByOwnerIdAndParentIdOrderByNameAsc(ownerId, item.getId()))
            collectSubtree(child, ownerId, output, visited);
        output.add(item);
    }

    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"); }
}
