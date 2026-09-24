package com.example.productauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_logs")
public class ScanLog {

    @Id
    private UUID id;

    @Column(name = "tag_uid", nullable = false, length = 14)
    private String tagUid;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    private Double latitude;

    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_result", nullable = false, length = 32)
    private ScanResult scanResult;

    @Column(name = "received_counter")
    private Integer receivedCounter;

    @Column(name = "expected_counter")
    private Integer expectedCounter;

    protected ScanLog() {
    }

    public ScanLog(String tagUid, Double latitude, Double longitude, ScanResult scanResult) {
        this(tagUid, latitude, longitude, scanResult, null, null);
    }

    public ScanLog(String tagUid, Double latitude, Double longitude, ScanResult scanResult,
            Integer receivedCounter, Integer expectedCounter) {
        this.id = UUID.randomUUID();
        this.tagUid = tagUid;
        this.latitude = latitude;
        this.longitude = longitude;
        this.scanResult = scanResult;
        this.receivedCounter = receivedCounter;
        this.expectedCounter = expectedCounter;
    }

    @PrePersist
    void assignTimestamp() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (scannedAt == null) {
            scannedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTagUid() {
        return tagUid;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public ScanResult getScanResult() {
        return scanResult;
    }

    public Integer getReceivedCounter() {
        return receivedCounter;
    }

    public Integer getExpectedCounter() {
        return expectedCounter;
    }
}
