package com.cfs.Drive_Service.Controller;

import com.cfs.Drive_Service.Model.ShareGrant;
import com.cfs.Drive_Service.Service.SharingService;
import com.cfs.Drive_Service.dto.ItemPageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SharingController {
    private final SharingService sharing;
    public SharingController(SharingService sharing) { this.sharing = sharing; }

    @PutMapping("/items/{id}/shares")
    public SharingService.ShareRecord share(@PathVariable UUID id, @Valid @RequestBody ShareRequest request, @AuthenticationPrincipal Jwt jwt) {
        return sharing.share(id, actor(jwt), request.email(), request.permission());
    }
    @GetMapping("/items/{id}/shares")
    public List<SharingService.ShareRecord> listShares(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return sharing.listShares(id, actor(jwt));
    }
    @DeleteMapping("/items/{id}/shares/{recipientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, @PathVariable UUID recipientId, @AuthenticationPrincipal Jwt jwt) {
        sharing.revoke(id, actor(jwt), recipientId);
    }
    @GetMapping("/shared-with-me")
    public ItemPageResponse sharedWithMe(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
        if (page < 0 || size < 1 || size > 100) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page settings");
        return ItemPageResponse.from(sharing.sharedWithMe(actor(jwt), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
    @GetMapping("/shared-with-me/sharers")
    public List<SharingService.SharerGroup> sharers(@AuthenticationPrincipal Jwt jwt) {
        return sharing.sharers(actor(jwt));
    }
    @GetMapping("/shared-with-me/sharers/{ownerId}/items")
    public ItemPageResponse sharedByOwner(@PathVariable UUID ownerId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        if (q != null && q.length() > 255) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text must be 255 characters or fewer");
        String sortBy = switch (sort.toLowerCase(java.util.Locale.ROOT)) {
            case "name" -> "name";
            case "date", "createdat" -> "createdAt";
            case "size" -> "size";
            default -> throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort must be name, date, or size");
        };
        Sort.Direction sortDirection;
        try { sortDirection = Sort.Direction.fromString(direction); }
        catch (IllegalArgumentException exception) { throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Direction must be asc or desc"); }
        if (page < 0 || size < 1 || size > 100) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page settings");
        return ItemPageResponse.from(sharing.sharedByOwner(actor(jwt), ownerId, q, sortBy, sortDirection, page, size));
    }
    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    public record ShareRequest(@Email @Size(max = 254) String email, @NotNull ShareGrant.Permission permission) {}
}
