package com.example.productauth.service;

import com.example.productauth.api.dto.AdminDtos;
import com.example.productauth.domain.ScanResult;
import com.example.productauth.domain.TagStatus;
import com.example.productauth.domain.NfcTag;
import com.example.productauth.repository.NfcTagRepository;
import com.example.productauth.repository.ScanLogRepository;
import com.example.productauth.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.example.productauth.api.AdminController.MetadataRequest;

@Service
public class AdminService {

    private final NfcTagRepository nfcTagRepository;
    private final ScanLogRepository scanLogRepository;
    private final ProductRepository productRepository;
    private final HsmClient hsmClient;

    public AdminService(NfcTagRepository nfcTagRepository, ScanLogRepository scanLogRepository,
            ProductRepository productRepository, HsmClient hsmClient) {
        this.nfcTagRepository = nfcTagRepository;
        this.scanLogRepository = scanLogRepository;
        this.productRepository = productRepository;
        this.hsmClient = hsmClient;
    }

    @Transactional(readOnly = true)
    public AdminDtos.Overview overview() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long scans = scanLogRepository.countDistinctEventsAfter(since);
        long successful = scanLogRepository.countDistinctSuccessfulEventsAfter(since);
        double successRate = scans == 0 ? 0 : (successful * 100.0) / scans;
        return new AdminDtos.Overview(
                nfcTagRepository.countByStatus(TagStatus.ACTIVE),
                nfcTagRepository.count(),
                scans,
                successful,
                Math.round(successRate * 10.0) / 10.0);
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.TagRow> tags(Pageable pageable, String uid) {
        return tags(pageable, new TagFilters(uid, "", null, "", "", null));
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.TagRow> tags(Pageable pageable, TagFilters filters) {
        Page<NfcTag> tags = nfcTagRepository.searchTags(
                blankToEmpty(filters.uid()), blankToEmpty(filters.query()), filters.productId(),
                blankToEmpty(filters.productName()), blankToEmpty(filters.manufacturer()),
                filters.status(), pageable);
        return tags.map(AdminDtos.TagRow::from);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.ScanRow> scans() {
        return scanLogRepository.findTop100ByOrderByScannedAtDesc().stream()
                .map(AdminDtos.ScanRow::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.ScanRow> securityEvents() {
        return scanLogRepository.findTop100ByOrderByScannedAtDesc().stream()
                .filter(log -> log.getScanResult() != ScanResult.REAL)
                .map(AdminDtos.ScanRow::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.LocationPoint> locations() {
        return scanLogRepository
                .findTop100ByScanResultAndGeoLatitudeIsNotNullAndGeoLongitudeIsNotNullOrderByScannedAtDesc(
                        ScanResult.REAL)
                .stream().map(log -> new AdminDtos.LocationPoint(log.getGeoLatitude(), log.getGeoLongitude()))
                .toList();
    }

    @Transactional
    public AdminDtos.TagRow revoke(String uid) {
        String normalizedUid = uid.toUpperCase(Locale.ROOT);
        var tag = nfcTagRepository.findByTagUid(normalizedUid)
                .orElseThrow(() -> new EntityNotFoundException("Tag was not found: " + normalizedUid));
        tag.setStatus(TagStatus.REVOKED);
        return AdminDtos.TagRow.from(nfcTagRepository.save(tag));
    }

    @Transactional
    public AdminDtos.TagRow activate(String uid) {
        String normalizedUid = uid.toUpperCase(Locale.ROOT);
        var tag = nfcTagRepository.findByTagUid(normalizedUid)
                .orElseThrow(() -> new EntityNotFoundException("Tag was not found: " + normalizedUid));
        tag.setStatus(TagStatus.ACTIVE);
        return AdminDtos.TagRow.from(nfcTagRepository.save(tag));
    }

    @Transactional
    public AdminDtos.TagRow updateMetadata(String uid, MetadataRequest request) {
        String normalizedUid = uid.toUpperCase(Locale.ROOT);
        var tag = nfcTagRepository.findByTagUid(normalizedUid)
                .orElseThrow(() -> new EntityNotFoundException("Tag was not found: " + normalizedUid));
        tag.setDisplayName(blankToNull(request.displayName()));
        tag.setDescription(blankToNull(request.description()));
        tag.setImageUrl(blankToNull(request.imageUrl()));
        return AdminDtos.TagRow.from(nfcTagRepository.save(tag));
    }

    @Transactional
    public void deleteTag(String uid) {
        String normalizedUid = uid.toUpperCase(Locale.ROOT);
        var tag = nfcTagRepository.findByTagUid(normalizedUid)
                .orElseThrow(() -> new EntityNotFoundException("Tag was not found: " + normalizedUid));
        hsmClient.deleteKey(normalizedUid);
        nfcTagRepository.delete(tag);
    }

    @Transactional
    public int bulkTags(List<String> uids, String action) {
        if (uids == null || uids.isEmpty()) {
            throw new IllegalArgumentException("At least one tag UID is required.");
        }
        if (uids.stream().anyMatch(uid -> uid == null || uid.isBlank())) {
            throw new IllegalArgumentException("Tag UIDs must not be blank.");
        }

        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("REVOKE", "ACTIVATE", "DELETE").contains(normalizedAction)) {
            throw new IllegalArgumentException("Action must be REVOKE, ACTIVATE, or DELETE.");
        }

        LinkedHashSet<String> normalizedUids = new LinkedHashSet<>();
        for (String uid : uids) {
            String normalizedUid = uid.trim().toUpperCase(Locale.ROOT);
            if (!normalizedUid.matches("[0-9A-F]{14}")) {
                throw new IllegalArgumentException("Invalid tag UID: " + uid);
            }
            normalizedUids.add(normalizedUid);
        }

        List<NfcTag> tags = nfcTagRepository.findByTagUidIn(new ArrayList<>(normalizedUids));
        if (tags.size() != normalizedUids.size()) {
            Set<String> foundUids = new LinkedHashSet<>();
            tags.forEach(tag -> foundUids.add(tag.getTagUid()));
            normalizedUids.removeAll(foundUids);
            throw new EntityNotFoundException("Tags were not found: " + String.join(", ", normalizedUids));
        }

        switch (normalizedAction) {
            case "REVOKE" -> tags.forEach(tag -> tag.setStatus(TagStatus.REVOKED));
            case "ACTIVATE" -> tags.forEach(tag -> tag.setStatus(TagStatus.ACTIVE));
            case "DELETE" -> {
                deleteHsmKeys(tags);
                nfcTagRepository.deleteAll(tags);
                nfcTagRepository.flush();
            }
            default -> throw new IllegalStateException("Unsupported tag action: " + normalizedAction);
        }
        return tags.size();
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product was not found: " + productId));
        List<NfcTag> tags = nfcTagRepository.findByProductId(productId);
        deleteHsmKeys(tags);
        nfcTagRepository.deleteAll(tags);
        nfcTagRepository.flush();
        productRepository.delete(product);
    }

    private void deleteHsmKeys(List<NfcTag> tags) {
        for (NfcTag tag : tags) {
            try {
                hsmClient.deleteKey(tag.getTagUid());
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "Could not delete HSM key for tag " + tag.getTagUid() + "; no tag records were deleted.",
                        exception);
            }
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record TagFilters(String uid, String query, UUID productId, String productName,
            String manufacturer, TagStatus status) {
    }
}
