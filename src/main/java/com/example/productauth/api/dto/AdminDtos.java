package com.example.productauth.api.dto;

import com.example.productauth.domain.NfcTag;
import com.example.productauth.domain.ScanLog;
import com.example.productauth.domain.ScanResult;
import com.example.productauth.domain.TagStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.example.productauth.domain.ProvisioningBatch;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record Overview(long activeTags, long totalTags, long scansLast24Hours,
                           long successfulScansLast24Hours, double successRate) {
    }

    public record TagRow(String uid, UUID productId, String productName, String displayName,
                         String description, String imageUrl, int lastScanCounter, TagStatus status) {
        public static TagRow from(NfcTag tag) {
            return new TagRow(tag.getTagUid(), tag.getProduct().getId(), tag.getProduct().getName(),
                    tag.getDisplayName(), tag.getDescription(), tag.getImageUrl(),
                    tag.getLastScanCounter(), tag.getStatus());
        }
    }

    public record ScanRow(String id, String uid, Instant timestamp, ScanResult result,
                          Double latitude, Double longitude, Integer receivedCounter,
                          Integer expectedCounter, String ipAddress, String geoCountry,
                          String geoCountryIsoCode, String geoRegion, String geoCity,
                          Double geoLatitude, Double geoLongitude,
                          Double deviceLatitude, Double deviceLongitude) {
        public static ScanRow from(ScanLog log) {
            return new ScanRow(log.getId().toString(), log.getTagUid(), log.getScannedAt(),
                    log.getScanResult(), log.getLatitude(), log.getLongitude(),
                    log.getReceivedCounter(), log.getExpectedCounter(), log.getIpAddress(),
                    log.getGeoCountry(), log.getGeoCountryIsoCode(), log.getGeoRegion(),
                    log.getGeoCity(), log.getGeoLatitude(), log.getGeoLongitude(),
                    log.getDeviceLatitude(), log.getDeviceLongitude());
        }
    }

    public record LocationPoint(double latitude, double longitude, String source, String timestamp) {
    }

    public record ActivityPoint(LocalDate day, long total, long verified, long flagged) {
    }

    public record ProductRow(UUID id, String name, String manufacturer, long tagCount) {
    }

    public record BulkActionResult(int affected) {
    }

    public record ProvisioningResult(UUID batchId, int totalRows, int importedRows,
            int duplicateRows, int invalidRows, String status) {
        public static ProvisioningResult from(ProvisioningBatch batch) {
            return new ProvisioningResult(batch.getId(), batch.getTotalRows(), batch.getImportedRows(),
                    batch.getDuplicateRows(), batch.getInvalidRows(), batch.getStatus());
        }
    }
}
