package com.cfs.Drive_Service.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drive_file_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "revision_number"}))
public class DriveFileVersion {
    @Id
    private UUID id;
    @Column(name = "item_id", nullable = false)
    private UUID itemId;
    @Column(name = "owner_id")
    private UUID ownerId;
    @Column(name = "object_id", nullable = false, unique = true)
    private UUID objectId;
    @Column(name = "revision_number", nullable = false)
    private long revisionNumber;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private long size;
    private String mediaType;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private UUID createdBy;

    protected DriveFileVersion() {}

    public DriveFileVersion(UUID itemId, UUID ownerId, UUID objectId, long revisionNumber, String name,
                            long size, String mediaType, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.itemId = itemId;
        this.ownerId = ownerId;
        this.objectId = objectId;
        this.revisionNumber = revisionNumber;
        this.name = name;
        this.size = size;
        this.mediaType = mediaType;
        this.createdAt = Instant.now();
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public UUID getItemId() { return itemId; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getObjectId() { return objectId; }
    public long getRevisionNumber() { return revisionNumber; }
    public String getName() { return name; }
    public long getSize() { return size; }
    public String getMediaType() { return mediaType; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
}
