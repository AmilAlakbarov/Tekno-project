package com.example.productauth.repository;

import com.example.productauth.domain.ScanLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.productauth.domain.ScanResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanLogRepository extends JpaRepository<ScanLog, UUID> {

    Optional<ScanLog> findTopByTagUidOrderByScannedAtDesc(String tagUid);

    Optional<ScanLog> findTopByTagUidAndScanResultOrderByScannedAtDesc(String tagUid, ScanResult scanResult);

    Optional<ScanLog> findTopByTagUidAndScanResultInOrderByScannedAtDesc(
            String tagUid, Collection<ScanResult> scanResults);

    Optional<ScanLog> findTopByTagUidAndScanResultAndIdNotOrderByScannedAtDesc(
            String tagUid, ScanResult scanResult, UUID excludedId);

    Optional<ScanLog> findTopByTagUidAndScanResultInAndIdNotOrderByScannedAtDesc(
            String tagUid, Collection<ScanResult> scanResults, UUID excludedId);

    Optional<ScanLog> findTopByTagUidAndReceivedCounterAndScanResultOrderByScannedAtDesc(
            String tagUid, Integer receivedCounter, ScanResult scanResult);

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

    List<ScanLog> findTop100ByScanResultAndGeoLatitudeIsNotNullAndGeoLongitudeIsNotNullOrderByScannedAtDesc(
            ScanResult scanResult);

    @Query(value = """
            WITH days AS (
                SELECT generate_series(
                    date_trunc('day', CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '6 days',
                    date_trunc('day', CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
                    INTERVAL '1 day'
                ) AS utc_day
            )
            SELECT CAST(days.utc_day AS date) AS day,
                   COUNT(scan.id) AS total,
                   COUNT(scan.id) FILTER (WHERE scan.scan_result = 'REAL') AS verified,
                   COUNT(scan.id) FILTER (WHERE scan.scan_result <> 'REAL') AS flagged
            FROM days
            LEFT JOIN scan_logs scan
                ON scan.scanned_at >= (days.utc_day AT TIME ZONE 'UTC')
               AND scan.scanned_at < ((days.utc_day + INTERVAL '1 day') AT TIME ZONE 'UTC')
            GROUP BY days.utc_day
            ORDER BY days.utc_day
            """, nativeQuery = true)
    List<DailyScanActivity> findDailyActivityForLastSevenDays();

    boolean existsByTagUidAndReceivedCounterAndScanResult(String tagUid, Integer receivedCounter,
            ScanResult scanResult);

    interface DailyScanActivity {
        LocalDate getDay();

        long getTotal();

        long getVerified();

        long getFlagged();
    }
}
