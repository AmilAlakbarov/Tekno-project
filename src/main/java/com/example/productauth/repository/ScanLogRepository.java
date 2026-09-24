package com.example.productauth.repository;

import com.example.productauth.domain.ScanLog;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.productauth.domain.ScanResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanLogRepository extends JpaRepository<ScanLog, UUID> {

    Optional<ScanLog> findTopByTagUidOrderByScannedAtDesc(String tagUid);

    long countByScannedAtAfter(Instant timestamp);

    long countByScannedAtAfterAndScanResult(Instant timestamp, ScanResult scanResult);

    List<ScanLog> findTop100ByOrderByScannedAtDesc();

    List<ScanLog> findTop100ByScanResultOrderByScannedAtDesc(ScanResult scanResult);

    List<ScanLog> findTop100ByLatitudeIsNotNullAndLongitudeIsNotNullOrderByScannedAtDesc();
}
