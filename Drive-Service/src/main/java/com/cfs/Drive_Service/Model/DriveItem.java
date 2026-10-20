package com.cfs.Drive_Service.Model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
public class DriveItem{

    public enum Kind{FILE,FOLDER}

    public enum Status{PENDING,READY,TRASHED,DELETING}

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    // Nullable only to allow existing H2 rows to be claimed by the first registered account.
    private UUID ownerId;

    public UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Convert(converter = DriveItemStatusConverter.class)
    @Column(nullable = false, length = 16)
    private Status status;

    private long size;
    private String mediaType;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant trashedAt;

    @Version
    private Long version;

    @Transient
    private String accessLevel;
    @Transient
    private String sharedOwnerEmail;

    protected DriveItem(){}

    public DriveItem(String name,UUID parentId,Kind kind,long size,String mediaType,UUID ownerId)
    {
        this.id=UUID.randomUUID();
        this.name=name;
        this.ownerId=ownerId;
        this.parentId=parentId;
        this.kind=kind;
        this.size=size;
        this.mediaType=mediaType;
        this.status = kind ==Kind.FOLDER ? Status.READY : Status.PENDING;
        this.createdAt=Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public UUID getOwnerId() { return ownerId; }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getTrashedAt() { return trashedAt; }

    public void setTrashedAt(Instant trashedAt) { this.trashedAt = trashedAt; }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public String getAccessLevel() { return accessLevel; }
    public void setAccessLevel(String accessLevel) { this.accessLevel = accessLevel; }
    public String getSharedOwnerEmail() { return sharedOwnerEmail; }
    public void setSharedOwnerEmail(String sharedOwnerEmail) { this.sharedOwnerEmail = sharedOwnerEmail; }

    public void setVersion(Long version) {
        this.version = version;
    }
}
