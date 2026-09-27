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

    // Client-submitted coordinates are an advisory travel signal, not verified GPS proof.
    private Double latitude;

    private Double longitude;

    @Column(name = "geo_country")
    private String geoCountry;

    @Column(name = "geo_country_iso_code", length = 2)
    private String geoCountryIsoCode;

    @Column(name = "geo_region")
    private String geoRegion;

    @Column(name = "geo_city")
    private String geoCity;

    @Column(name = "geo_latitude")
    private Double geoLatitude;

    @Column(name = "geo_longitude")
    private Double geoLongitude;

    @Column(name = "device_latitude")
    private Double deviceLatitude;

    @Column(name = "device_longitude")
    private Double deviceLongitude;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

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
        this(tagUid, latitude, longitude, null, scanResult, receivedCounter, expectedCounter);
    }

    public ScanLog(String tagUid, Double latitude, Double longitude, String ipAddress,
            ScanResult scanResult, Integer receivedCounter, Integer expectedCounter) {
        this(tagUid, latitude, longitude, ipAddress, scanResult, receivedCounter, expectedCounter, null);
    }

    public ScanLog(String tagUid, Double latitude, Double longitude, String ipAddress,
            ScanResult scanResult, Integer receivedCounter, Integer expectedCounter,
            GeoIpLocation geoLocation) {
        this.id = UUID.randomUUID();
        this.tagUid = tagUid;
        this.latitude = latitude;
        this.longitude = longitude;
        if (scanResult == ScanResult.REAL && geoLocation != null) {
            this.geoCountry = geoLocation.country();
            this.geoCountryIsoCode = geoLocation.countryIsoCode();
            this.geoRegion = geoLocation.region();
            this.geoCity = geoLocation.city();
            this.geoLatitude = geoLocation.latitude();
            this.geoLongitude = geoLocation.longitude();
        }
        this.ipAddress = ipAddress;
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

    public String getGeoCountry() {
        return geoCountry;
    }

    public String getGeoCountryIsoCode() {
        return geoCountryIsoCode;
    }

    public String getGeoRegion() {
        return geoRegion;
    }

    public String getGeoCity() {
        return geoCity;
    }

    public Double getGeoLatitude() {
        return geoLatitude;
    }

    public Double getGeoLongitude() {
        return geoLongitude;
    }

    public Double getDeviceLatitude() {
        return deviceLatitude;
    }

    public Double getDeviceLongitude() {
        return deviceLongitude;
    }

    public void setDeviceLocation(double latitude, double longitude) {
        if (scanResult != ScanResult.REAL) {
            throw new IllegalStateException("Device location can only be attached to a verified scan.");
        }
        if (deviceLatitude != null || deviceLongitude != null) {
            throw new IllegalStateException("Device location has already been submitted for this scan.");
        }
        this.deviceLatitude = latitude;
        this.deviceLongitude = longitude;
    }

    public String getIpAddress() {
        return ipAddress;
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
