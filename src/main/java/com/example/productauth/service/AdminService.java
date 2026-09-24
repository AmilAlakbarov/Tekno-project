package com.example.productauth.service;

import com.example.productauth.api.dto.AdminDtos;
import com.example.productauth.domain.ScanResult;
import com.example.productauth.domain.TagStatus;
import com.example.productauth.domain.NfcTag;
import com.example.productauth.repository.NfcTagRepository;
import com.example.productauth.repository.ScanLogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class AdminService {

    private final NfcTagRepository nfcTagRepository;
    private final ScanLogRepository scanLogRepository;

    public AdminService(NfcTagRepository nfcTagRepository, ScanLogRepository scanLogRepository) {
        this.nfcTagRepository = nfcTagRepository;
        this.scanLogRepository = scanLogRepository;
    }

    @Transactional(readOnly = true)
    public AdminDtos.Overview overview() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long scans = scanLogRepository.countByScannedAtAfter(since);
        long successful = scanLogRepository.countByScannedAtAfterAndScanResult(since, ScanResult.REAL);
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
        Page<NfcTag> tags = uid == null || uid.isBlank()
                ? nfcTagRepository.findAll(pageable)
                : nfcTagRepository.findByTagUidContainingIgnoreCase(uid.trim(), pageable);
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
        return scanLogRepository.findTop100ByLatitudeIsNotNullAndLongitudeIsNotNullOrderByScannedAtDesc()
                .stream().map(log -> new AdminDtos.LocationPoint(log.getLatitude(), log.getLongitude())).toList();
    }

    @Transactional
    public AdminDtos.TagRow revoke(String uid) {
        String normalizedUid = uid.toUpperCase(Locale.ROOT);
        var tag = nfcTagRepository.findByTagUid(normalizedUid)
                .orElseThrow(() -> new EntityNotFoundException("Tag was not found: " + normalizedUid));
        tag.setStatus(TagStatus.REVOKED);
        return AdminDtos.TagRow.from(nfcTagRepository.save(tag));
    }
}
