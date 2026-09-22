package com.example.productauth.repository;

import com.example.productauth.domain.ScanLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScanLogRepository extends JpaRepository<ScanLog, UUID> {

    Optional<ScanLog> findTopByTagUidOrderByScannedAtDesc(String tagUid);
}
