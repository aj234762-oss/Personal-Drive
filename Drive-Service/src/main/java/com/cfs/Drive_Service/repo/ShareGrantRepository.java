package com.cfs.Drive_Service.repo;

import com.cfs.Drive_Service.Model.ShareGrant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareGrantRepository extends JpaRepository<ShareGrant, UUID> {
    Optional<ShareGrant> findByItemIdAndRecipientId(UUID itemId, UUID recipientId);
    List<ShareGrant> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<ShareGrant> findByItemIdOrderByCreatedAtAsc(UUID itemId);
    void deleteByItemIdAndRecipientId(UUID itemId, UUID recipientId);
}
