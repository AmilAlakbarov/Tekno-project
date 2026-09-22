package com.example.productauth.service;

import com.example.productauth.api.dto.ProductSummary;
import com.example.productauth.api.dto.VerifyRequest;
import com.example.productauth.api.dto.VerifyResponse;
import com.example.productauth.domain.NfcTag;
import com.example.productauth.domain.ScanLog;
import com.example.productauth.domain.ScanResult;
import com.example.productauth.domain.TagStatus;
import com.example.productauth.repository.NfcTagRepository;
import com.example.productauth.repository.ScanLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

@Service
public class VerificationService {

    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double MAX_SPEED_KMH = 1000.0;

    private final NfcTagRepository nfcTagRepository;
    private final ScanLogRepository scanLogRepository;
    private final SignatureVerificationService signatureVerificationService;
    private final FirebaseScanPublisher firebaseScanPublisher;

    public VerificationService(NfcTagRepository nfcTagRepository,
            ScanLogRepository scanLogRepository,
            SignatureVerificationService signatureVerificationService,
            FirebaseScanPublisher firebaseScanPublisher) {
        this.nfcTagRepository = nfcTagRepository;
        this.scanLogRepository = scanLogRepository;
        this.signatureVerificationService = signatureVerificationService;
        this.firebaseScanPublisher = firebaseScanPublisher;
    }

    @Transactional
    public VerifyResponse verify(VerifyRequest request) {
        String uid = request.uid().toUpperCase();
        int counter = parseCounter(request.ctr());
        Optional<NfcTag> tagOptional = nfcTagRepository.findByTagUid(uid);

        if (tagOptional.isEmpty()) {
            return recordFailure(request, ScanResult.NOT_FOUND, null, "Product could not be found.");
        }

        NfcTag tag = tagOptional.get();
        if (tag.getStatus() != TagStatus.ACTIVE) {
            return recordFailure(request, ScanResult.TAMPERED, tag, "Product authentication failed.");
        }
        if (counter <= tag.getLastScanCounter()) {
            return recordFailure(request, ScanResult.REPLAY_ATTACK, tag, "Replay attack detected.");
        }

        if (hasSpeedAnomaly(request, uid)) {
            return recordFailure(request, ScanResult.SPEED_ANOMALY, tag, "Impossible travel speed detected.");
        }

        if (!signatureVerificationService.matches(uid, counter, tag.getAesKey(), request.cmac())) {
            return recordFailure(request, ScanResult.TAMPERED, tag, "Product authentication failed.");
        }

        tag.setLastScanCounter(counter);
        nfcTagRepository.save(tag);
        ScanLog scanLog = saveLog(request, ScanResult.REAL);
        firebaseScanPublisher.publish(scanLog, tag.getProduct().getId().toString());
        return VerifyResponse.real(new ProductSummary(tag.getProduct().getName(), tag.getProduct().getManufacturer()));
    }

    private VerifyResponse recordFailure(VerifyRequest request, ScanResult result, NfcTag tag, String message) {
        ScanLog scanLog = saveLog(request, result);
        firebaseScanPublisher.publish(scanLog, tag == null ? null : tag.getProduct().getId().toString());
        return VerifyResponse.fake(message);
    }

    private ScanLog saveLog(VerifyRequest request, ScanResult result) {
        return scanLogRepository.save(new ScanLog(
                request.uid().toUpperCase(), request.latitude(), request.longitude(), result));
    }

    private boolean hasSpeedAnomaly(VerifyRequest request, String uid) {
        Optional<ScanLog> previousOptional = scanLogRepository.findTopByTagUidOrderByScannedAtDesc(uid);
        if (previousOptional.isEmpty()) {
            return false;
        }

        ScanLog previous = previousOptional.get();
        if (previous.getLatitude() == null || previous.getLongitude() == null) {
            return false;
        }

        double distanceKm = haversineKm(previous.getLatitude(), previous.getLongitude(),
                request.latitude(), request.longitude());
        long elapsedSeconds = Duration.between(previous.getScannedAt(), java.time.Instant.now()).getSeconds();
        if (elapsedSeconds <= 0) {
            return distanceKm > 0.001;
        }
        double speedKmh = distanceKm / (elapsedSeconds / 3600.0);
        return speedKmh > MAX_SPEED_KMH;
    }

    static double haversineKm(double firstLatitude, double firstLongitude,
            double secondLatitude, double secondLongitude) {
        double latitudeDistance = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDistance = Math.toRadians(secondLongitude - firstLongitude);
        double a = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(firstLatitude)) * Math.cos(Math.toRadians(secondLatitude))
                        * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static int parseCounter(String counter) {
        return Integer.parseInt(counter, 16);
    }
}
