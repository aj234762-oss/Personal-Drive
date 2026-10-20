package com.cfs.Drive_Service.repo;

import com.cfs.Drive_Service.Model.DriveItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<DriveItem, UUID> {
    List<DriveItem> findByOwnerIdAndParentIdAndStatusOrderByNameAsc(UUID ownerId, UUID parentId, DriveItem.Status status);
    @Query(value = "select i from DriveItem i where i.ownerId = :ownerId and i.status = :status " +
            "and ((:parentId is null and i.parentId is null) or i.parentId = :parentId) " +
            "and (:query is null or lower(i.name) like lower(concat('%', :query, '%'))) ",
            countQuery = "select count(i) from DriveItem i where i.ownerId = :ownerId and i.status = :status " +
                    "and ((:parentId is null and i.parentId is null) or i.parentId = :parentId) " +
                    "and (:query is null or lower(i.name) like lower(concat('%', :query, '%'))) ")
    Page<DriveItem> searchVisibleItems(@Param("ownerId") UUID ownerId,
                                       @Param("parentId") UUID parentId,
                                       @Param("status") DriveItem.Status status,
                                       @Param("query") String query,
                                       Pageable pageable);
    @Query(value = "select i from DriveItem i where i.parentId = :parentId and i.status = :status " +
            "and (:query is null or lower(i.name) like lower(concat('%', :query, '%'))) ",
            countQuery = "select count(i) from DriveItem i where i.parentId = :parentId and i.status = :status " +
                    "and (:query is null or lower(i.name) like lower(concat('%', :query, '%'))) ")
    Page<DriveItem> searchChildren(@Param("parentId") UUID parentId, @Param("status") DriveItem.Status status,
                                   @Param("query") String query, Pageable pageable);
    List<DriveItem> findByOwnerIdAndStatusOrderByNameAsc(UUID ownerId, DriveItem.Status status);
    List<DriveItem> findByOwnerIdAndParentIdOrderByNameAsc(UUID ownerId, UUID parentId);
    boolean existsByOwnerIdAndParentId(UUID ownerId, UUID parentId);

    @Query("select coalesce(sum(i.size), 0) from DriveItem i where i.ownerId = :ownerId " +
            "and i.kind = :kind and i.status in :statuses")
    long sumStoredFileBytesByOwnerId(@Param("ownerId") UUID ownerId,
                                     @Param("kind") DriveItem.Kind kind,
                                     @Param("statuses") List<DriveItem.Status> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from DriveItem i where i.id=:id and i.ownerId=:ownerId")
    Optional<DriveItem> lockByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Modifying
    @Query("update DriveItem i set i.ownerId=:ownerId where i.ownerId is null")
    int claimUnownedItems(@Param("ownerId") UUID ownerId);
}
