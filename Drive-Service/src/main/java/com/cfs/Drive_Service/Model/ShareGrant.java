package com.cfs.Drive_Service.Model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drive_share_grants", uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "recipient_id"}))
public class ShareGrant {
    public enum Permission { VIEWER, EDITOR }

    @Id
    private UUID id;
    @Column(name = "item_id", nullable = false)
    private UUID itemId;
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Permission permission;
    @Column(nullable = false)
    private Instant createdAt;

    protected ShareGrant() {}
    public ShareGrant(UUID itemId, UUID recipientId, Permission permission) {
        this.id = UUID.randomUUID(); this.itemId = itemId; this.recipientId = recipientId;
        this.permission = permission; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getItemId() { return itemId; }
    public UUID getRecipientId() { return recipientId; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
    public Instant getCreatedAt() { return createdAt; }
}
