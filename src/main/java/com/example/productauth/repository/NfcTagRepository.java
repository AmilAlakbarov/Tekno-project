package com.example.productauth.repository;

import com.example.productauth.domain.NfcTag;
import com.example.productauth.domain.TagStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface NfcTagRepository extends JpaRepository<NfcTag, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NfcTag> findByTagUid(String tagUid);

    boolean existsByTagUid(String tagUid);

    List<NfcTag> findByProductId(UUID productId);

    List<NfcTag> findByTagUidIn(List<String> tagUids);

    long countByProductId(UUID productId);

    Page<NfcTag> findByTagUidContainingIgnoreCase(String tagUid, Pageable pageable);

    long countByStatus(TagStatus status);

    @Query("""
            SELECT tag FROM NfcTag tag
            JOIN tag.product product
            WHERE (:uid = '' OR LOWER(tag.tagUid) LIKE LOWER(CONCAT('%', :uid, '%')))
              AND (:q = ''
                   OR LOWER(tag.tagUid) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(product.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(product.manufacturer) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(tag.displayName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(tag.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(CAST(product.id AS string)) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:productId IS NULL OR product.id = :productId)
              AND (:productName = '' OR LOWER(product.name) LIKE LOWER(CONCAT('%', :productName, '%')))
              AND (:manufacturer = '' OR LOWER(product.manufacturer) LIKE LOWER(CONCAT('%', :manufacturer, '%')))
              AND (:status IS NULL OR tag.status = :status)
            """)
    Page<NfcTag> searchTags(@Param("uid") String uid, @Param("q") String query,
            @Param("productId") UUID productId, @Param("productName") String productName,
            @Param("manufacturer") String manufacturer, @Param("status") TagStatus status,
            Pageable pageable);
}
