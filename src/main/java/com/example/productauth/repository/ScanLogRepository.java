package com.example.productauth.repository;

import com.example.productauth.domain.ScanLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.productauth.domain.ScanResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanLogRepository extends JpaRepository<ScanLog, UUID> {

    Optional<ScanLog> findTopByTagUidOrderByScannedAtDesc(String tagUid);

    long countByScannedAtAfter(Instant timestamp);

    long countByScannedAtAfterAndScanResult(Instant timestamp, ScanResult scanResult);

    @Query(value = """
            SELECT COUNT(DISTINCT CASE
                WHEN received_counter IS NULL THEN id::text
                ELSE tag_uid || ':' || received_counter || ':' || scan_result
            END)
            FROM scan_logs
            WHERE scanned_at > :since
            """, nativeQuery = true)
    long countDistinctEventsAfter(@Param("since") Instant since);

    @Query(value = """
            SELECT COUNT(DISTINCT CASE
                WHEN received_counter IS NULL THEN id::text
                ELSE tag_uid || ':' || received_counter || ':' || scan_result
            END)
            FROM scan_logs
            WHERE scanned_at > :since AND scan_result = 'REAL'
            """, nativeQuery = true)
    long countDistinctSuccessfulEventsAfter(@Param("since") Instant since);

    List<ScanLog> findTop100ByOrderByScannedAtDesc();

    List<ScanLog> findTop100ByScanResultOrderByScannedAtDesc(ScanResult scanResult);

    List<ScanLog> findTop100ByLatitudeIsNotNullAndLongitudeIsNotNullOrderByScannedAtDesc();

    boolean existsByTagUidAndReceivedCounterAndScanResult(String tagUid, Integer receivedCounter,
            ScanResult scanResult);
}
