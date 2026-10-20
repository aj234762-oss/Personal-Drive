package com.cfs.Drive_Service.Controller;

import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.Service.TrashService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trash")
public class TrashController {
    private final TrashService trash;

    public TrashController(TrashService trash) { this.trash = trash; }

    @GetMapping
    public List<DriveItem> list(@AuthenticationPrincipal Jwt jwt) {
        return trash.list(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{id}/restore")
    public DriveItem restore(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return trash.restore(id, UUID.fromString(jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permanentlyDelete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        trash.permanentlyDelete(id, UUID.fromString(jwt.getSubject()));
    }
}
