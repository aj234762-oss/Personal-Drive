package com.cfs.Drive_Service.repo;

import com.cfs.Drive_Service.Model.DriveFileVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriveFileVersionRepository extends JpaRepository<DriveFileVersion, UUID> {
    List<DriveFileVersion> findByItemIdAndOwnerIdOrderByRevisionNumberDesc(UUID itemId, UUID ownerId);
    List<DriveFileVersion> findAllByItemIdOrderByRevisionNumberDesc(UUID itemId);
    Optional<DriveFileVersion> findByIdAndItemIdAndOwnerId(UUID id, UUID itemId, UUID ownerId);
    long countByItemIdAndOwnerId(UUID itemId, UUID ownerId);

    @Query("select coalesce(sum(v.size), 0) from DriveFileVersion v where v.ownerId = :ownerId")
    long sumStoredVersionBytesByOwnerId(@Param("ownerId") UUID ownerId);
}
