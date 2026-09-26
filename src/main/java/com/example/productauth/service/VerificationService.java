package com.example.productauth.service;

import com.example.productauth.api.dto.ProductSummary;
import com.example.productauth.api.dto.VerifyRequest;
import com.example.productauth.api.dto.VerifyResponse;
import com.example.productauth.domain.NfcTag;
import com.example.productauth.domain.GeoIpLocation;
import com.example.productauth.domain.ScanLog;
import com.example.productauth.domain.ScanResult;
import com.example.productauth.domain.TagStatus;
import com.example.productauth.repository.NfcTagRepository;
import com.example.productauth.repository.ScanLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double MAX_SPEED_KMH = 1000.0;
    private static final int MAX_COUNTER_JUMP = 1000;

    private final NfcTagRepository nfcTagRepository;
    private final ScanLogRepository scanLogRepository;
    private final SignatureVerificationService signatureVerificationService;
    private final HsmClient hsmClient;
    private final GeoIpService geoIpService;

    public VerificationService(NfcTagRepository nfcTagRepository,
            ScanLogRepository scanLogRepository,
            SignatureVerificationService signatureVerificationService, HsmClient hsmClient,
            GeoIpService geoIpService) {
        this.nfcTagRepository = nfcTagRepository;
        this.scanLogRepository = scanLogRepository;
        this.signatureVerificationService = signatureVerificationService;
        this.hsmClient = hsmClient;
        this.geoIpService = geoIpService;
    }

    @Transactional
    public VerifyResponse verify(VerifyRequest request) {
        return verify(request, null);
    }

    @Transactional
    public VerifyResponse verify(VerifyRequest request, String ipAddress) {
        String uid = request.uid().toUpperCase();
        int counter = parseCounter(request.ctr());
        Optional<NfcTag> tagOptional = nfcTagRepository.findByTagUid(uid);

        if (tagOptional.isEmpty()) {
            return recordFailure(request, ipAddress, ScanResult.NOT_FOUND, null, "Product could not be found.", counter, null);
        }

        NfcTag tag = tagOptional.get();
        if (tag.getStatus() != TagStatus.ACTIVE) {
            return recordFailure(request, ipAddress, ScanResult.TAMPERED, tag, "Product authentication failed.", counter, tag.getLastScanCounter() + 1);
        }
        if (counter <= tag.getLastScanCounter()) {
            return recordFailure(request, ipAddress, ScanResult.REPLAY_ATTACK, tag, "Replay attack detected.", counter, tag.getLastScanCounter() + 1);
        }

        if (!signatureVerificationService.matches(uid, counter, tag.getAesKey(), request.cmac())) {
            return recordFailure(request, ipAddress, ScanResult.TAMPERED, tag, "Product authentication failed.", counter, tag.getLastScanCounter() + 1);
        }

        Optional<GeoIpLocation> geoLocation = geoIpService.lookup(ipAddress);
        if (hasTravelAnomaly(uid, geoLocation)) {
            return recordFailure(request, ipAddress, ScanResult.SPEED_ANOMALY, tag,
                    "Impossible travel speed detected.", counter, tag.getLastScanCounter() + 1);
        }

        tag.setLastScanCounter(counter);
        nfcTagRepository.save(tag);
        saveSuccessfulLog(request.uid().toUpperCase(), request.latitude(), request.longitude(), ipAddress,
                counter, counter + 1, geoLocation.orElse(null));
        return VerifyResponse.real(new ProductSummary(tag.getProduct().getName(), tag.getProduct().getManufacturer()));
    }

    @Transactional
    public VerifyResponse verifySdm(String uid, String counterHex, String incomingCmac, String macInput) {
        return verifySdm(uid, counterHex, incomingCmac, macInput, null);
    }

    @Transactional
    public VerifyResponse verifySdm(String uid, String counterHex, String incomingCmac, String macInput,
            String ipAddress) {
        String normalizedUid = uid.toUpperCase(Locale.ROOT);
        String normalizedCounter = counterHex.toUpperCase(Locale.ROOT);
        String normalizedCmac = incomingCmac.toUpperCase(Locale.ROOT);

        Optional<NfcTag> tagOptional = nfcTagRepository.findByTagUid(normalizedUid);
        if (tagOptional.isEmpty()) {
            return recordFailure(normalizedUid, ipAddress, ScanResult.NOT_FOUND, null, "Product could not be found.",
                    counterOrZero(normalizedCounter), null);
        }

        NfcTag tag = tagOptional.get();
        if (tag.getStatus() != TagStatus.ACTIVE) {
            return recordFailure(normalizedUid, ipAddress, ScanResult.TAMPERED, tag, "Product authentication failed.", counterOrZero(normalizedCounter), tag.getLastScanCounter() + 1);
        }

        int counter;
        try {
            counter = parseCounter(normalizedCounter);
        } catch (NumberFormatException exception) {
            return recordFailure(normalizedUid, ipAddress, ScanResult.TAMPERED, tag, "Product authentication failed.",
                    counterOrZero(normalizedCounter), tag.getLastScanCounter() + 1);
        }

        if (counter <= tag.getLastScanCounter()) {
            return recordFailure(normalizedUid, ipAddress, ScanResult.REPLAY_ATTACK, tag, "Replay attack detected.", counter, tag.getLastScanCounter() + 1);
        }

        boolean signatureValid = hsmClient.isConfigured()
                ? hsmClient.verifyNtag424(normalizedUid, normalizedCounter, macInput, normalizedCmac)
                : signatureVerificationService.matchesNtag424Sdm(
                        normalizedUid, normalizedCounter, macInput, normalizedCmac, tag.getAesKey());
        if (!signatureValid) {
            return recordFailure(normalizedUid, ipAddress, ScanResult.TAMPERED, tag, "Product authentication failed.", counter, tag.getLastScanCounter() + 1);
        }

        if (counter - tag.getLastScanCounter() > MAX_COUNTER_JUMP) {
            return recordFailure(normalizedUid, ipAddress, ScanResult.SPEED_ANOMALY, tag,
                    "Suspicious counter jump detected.", counter, tag.getLastScanCounter() + 1);
        }

        Optional<GeoIpLocation> geoLocation = geoIpService.lookup(ipAddress);
        if (hasTravelAnomaly(normalizedUid, geoLocation)) {
            return recordFailure(normalizedUid, ipAddress, ScanResult.SPEED_ANOMALY, tag,
                    "Impossible travel speed detected.", counter, tag.getLastScanCounter() + 1);
        }

        tag.setLastScanCounter(counter);
        nfcTagRepository.save(tag);
        saveSuccessfulLog(normalizedUid, null, null, ipAddress, counter, counter + 1,
                geoLocation.orElse(null));
        return VerifyResponse.real(new ProductSummary(tag.getProduct().getName(), tag.getProduct().getManufacturer()));
    }

    private VerifyResponse recordFailure(VerifyRequest request, String ipAddress, ScanResult result, NfcTag tag,
            String message, Integer receivedCounter, Integer expectedCounter) {
        saveIfNew(request.uid().toUpperCase(), request.latitude(), request.longitude(), ipAddress, result,
                receivedCounter, expectedCounter);
        return VerifyResponse.fake(message);
    }

    private VerifyResponse recordFailure(String uid, String ipAddress, ScanResult result, NfcTag tag,
            String message, Integer receivedCounter, Integer expectedCounter) {
        saveIfNew(uid, null, null, ipAddress, result, receivedCounter, expectedCounter);
        return VerifyResponse.fake(message);
    }

    private ScanLog saveSuccessfulLog(String uid, Double latitude, Double longitude, String ipAddress,
            Integer receivedCounter, Integer expectedCounter, GeoIpLocation geoLocation) {
        if (scanLogRepository.existsByTagUidAndReceivedCounterAndScanResult(uid, receivedCounter, ScanResult.REAL)) {
            return null;
        }
        return scanLogRepository.save(new ScanLog(uid, latitude, longitude, ipAddress,
                ScanResult.REAL, receivedCounter, expectedCounter, geoLocation));
    }

    private int counterOrZero(String counter) {
        try {
            return parseCounter(counter);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private ScanLog saveIfNew(String uid, Double latitude, Double longitude, String ipAddress,
            ScanResult result, Integer receivedCounter, Integer expectedCounter) {
        if (receivedCounter != null
                && scanLogRepository.existsByTagUidAndReceivedCounterAndScanResult(uid, receivedCounter, result)) {
            return null;
        }
        return scanLogRepository.save(new ScanLog(uid, latitude, longitude, ipAddress, result,
                receivedCounter, expectedCounter));
    }

    private boolean hasTravelAnomaly(String uid, Optional<GeoIpLocation> currentLocation) {
        if (currentLocation.isEmpty()) {
            log.info("Travel anomaly check skipped: verified scan has no GeoIP coordinates.");
            return false;
        }

        Optional<ScanLog> previousOptional = scanLogRepository
                .findTopByTagUidAndScanResultOrderByScannedAtDesc(uid, ScanResult.REAL);
        if (previousOptional.isEmpty()) {
            return false;
        }

        ScanLog previous = previousOptional.get();
        if (previous.getGeoLatitude() == null || previous.getGeoLongitude() == null) {
            log.info("Travel anomaly check skipped: latest successful scan has no GeoIP coordinates.");
            return false;
        }

        GeoIpLocation location = currentLocation.get();
        if (location.latitude() == null || location.longitude() == null) {
            log.info("Travel anomaly check skipped: GeoIP location has no coordinates.");
            return false;
        }
        double distanceKm = haversineKm(previous.getGeoLatitude(), previous.getGeoLongitude(),
                location.latitude(), location.longitude());
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
