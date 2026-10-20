package com.cfs.Drive_Service.dto;

import com.cfs.Drive_Service.Model.DriveFileVersion;
import java.time.Instant;
import java.util.UUID;

public record FileVersionResponse(UUID id, long revisionNumber, String name, long size,
                                  String mediaType, Instant createdAt) {
    public static FileVersionResponse from(DriveFileVersion version) {
        return new FileVersionResponse(version.getId(), version.getRevisionNumber(), version.getName(),
                version.getSize(), version.getMediaType(), version.getCreatedAt());
    }
}
