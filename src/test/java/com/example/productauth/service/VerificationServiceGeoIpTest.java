package com.example.productauth.service;

import com.example.productauth.api.dto.VerifyRequest;
import com.example.productauth.api.dto.VerifyResponse;
import com.example.productauth.domain.GeoIpLocation;
import com.example.productauth.domain.NfcTag;
import com.example.productauth.domain.Product;
import com.example.productauth.domain.ScanLog;
import com.example.productauth.domain.ScanResult;
import com.example.productauth.domain.TagStatus;
import com.example.productauth.repository.NfcTagRepository;
import com.example.productauth.repository.ScanLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationServiceGeoIpTest {

    private final NfcTagRepository tags = mock(NfcTagRepository.class);
    private final ScanLogRepository logs = mock(ScanLogRepository.class);
    private final SignatureVerificationService signatures = mock(SignatureVerificationService.class);
    private final HsmClient hsmClient = mock(HsmClient.class);
    private final GeoIpService geoIp = mock(GeoIpService.class);
    private final VerificationService service = new VerificationService(
            tags, logs, signatures, hsmClient, geoIp);
    private final Product product = new Product(UUID.randomUUID(), "Product", "Maker", null);
    private final NfcTag tag = new NfcTag(UUID.randomUUID(), product, "04AABBCCDDEEFF", "00112233445566778899AABBCCDDEEFF");
    private final VerifyRequest request = new VerifyRequest("04AABBCCDDEEFF", "000001",
            "0011223344556677", 40.0, 50.0);
    private final GeoIpLocation location = new GeoIpLocation(
            "Country", "CC", "Region", "City", 40.0, 50.0);

    @BeforeEach
    void setUp() {
        tag.setStatus(TagStatus.ACTIVE);
        when(tags.findByTagUid("04AABBCCDDEEFF")).thenReturn(Optional.of(tag));
        when(logs.existsByTagUidAndReceivedCounterAndScanResult(
                "04AABBCCDDEEFF", 1, ScanResult.REAL)).thenReturn(false);
        when(logs.existsByTagUidAndReceivedCounterAndScanResult(
                "04AABBCCDDEEFF", 1, ScanResult.TAMPERED)).thenReturn(false);
        when(logs.existsByTagUidAndReceivedCounterAndScanResult(
                "04AABBCCDDEEFF", 1, ScanResult.SPEED_ANOMALY)).thenReturn(false);
        when(logs.save(any(ScanLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hsmClient.isConfigured()).thenReturn(false);
    }

    @Test
    void rejectsInvalidSignatureBeforeGeoIpOrTravelLookup() {
        when(signatures.matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac())).thenReturn(false);

        VerifyResponse response = service.verify(request, "8.8.8.8");

        assertThat(response.status()).isEqualTo("FAKE");
        verify(geoIp, never()).lookup(any());
        verify(logs, never()).findTopByTagUidAndScanResultInOrderByScannedAtDesc(any(), any());
        assertThat(capturedScanLog().getScanResult()).isEqualTo(ScanResult.TAMPERED);
        assertThat(capturedScanLog().getGeoCountry()).isNull();
        assertThat(tag.getLastScanCounter()).isZero();
    }

    @Test
    void recordsInvalidJsonCmacAsTamperedEvenWhenItsCounterWasAlreadyAccepted() {
        tag.setLastScanCounter(1);
        when(signatures.matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac())).thenReturn(false);

        VerifyResponse response = service.verify(request, "74.125.208.230");

        assertThat(response.status()).isEqualTo("FAKE");
        assertThat(capturedScanLog().getScanResult()).isEqualTo(ScanResult.TAMPERED);
        verify(signatures).matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac());
    }

    @Test
    void rejectsButDoesNotLogReplayFromDifferentSourceIp() {
        tag.setLastScanCounter(12);
        when(signatures.matchesNtag424Sdm("04AABBCCDDEEFF", "00000C", "mac-input",
                "0011223344556677", tag.getAesKey())).thenReturn(true);
        ScanLog latestVerifiedScan = mock(ScanLog.class);
        when(latestVerifiedScan.getIpAddress()).thenReturn("8.8.8.8");
        when(logs.findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY)))
                .thenReturn(Optional.of(latestVerifiedScan));
        VerifyResponse response = service.verifySdm(
                "04AABBCCDDEEFF", "00000C", "0011223344556677", "mac-input", "74.125.208.230");

        assertThat(response.status()).isEqualTo("FAKE");
        verify(logs, never()).save(any(ScanLog.class));
        verify(signatures).matchesNtag424Sdm("04AABBCCDDEEFF", "00000C", "mac-input",
                "0011223344556677", tag.getAesKey());
    }

    @Test
    void keepsLoggingReplayFromSameSourceIpAsLatestVerifiedScan() {
        tag.setLastScanCounter(12);
        when(signatures.matchesNtag424Sdm("04AABBCCDDEEFF", "00000C", "mac-input",
                "0011223344556677", tag.getAesKey())).thenReturn(true);
        ScanLog latestVerifiedScan = mock(ScanLog.class);
        when(latestVerifiedScan.getIpAddress()).thenReturn("8.8.8.8");
        when(logs.findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY)))
                .thenReturn(Optional.of(latestVerifiedScan));
        VerifyResponse response = service.verifySdm(
                "04AABBCCDDEEFF", "00000C", "0011223344556677", "mac-input", "8.8.8.8");

        assertThat(response.status()).isEqualTo("FAKE");
        assertThat(capturedScanLog().getScanResult()).isEqualTo(ScanResult.REPLAY_ATTACK);
    }

    @Test
    void recordsTamperedCmacBeforeClassifyingTheReusedCounterAsReplay() {
        tag.setLastScanCounter(12);
        when(signatures.matchesNtag424Sdm("04AABBCCDDEEFF", "00000C", "mac-input",
                "1011223344556677", tag.getAesKey())).thenReturn(false);

        VerifyResponse response = service.verifySdm(
                "04AABBCCDDEEFF", "00000C", "1011223344556677", "mac-input", "74.125.208.230");

        assertThat(response.status()).isEqualTo("FAKE");
        assertThat(capturedScanLog().getScanResult()).isEqualTo(ScanResult.TAMPERED);
        verify(signatures).matchesNtag424Sdm("04AABBCCDDEEFF", "00000C", "mac-input",
                "1011223344556677", tag.getAesKey());
    }

    @Test
    void recordsMalformedCmacAsTamperedWithoutCallingHsm() {
        tag.setLastScanCounter(12);
        when(hsmClient.isConfigured()).thenReturn(true);

        VerifyResponse response = service.verifySdm(
                "04AABBCCDDEEFF", "00000C", "not-a-cmac", "mac-input", "8.8.8.8");

        assertThat(response.status()).isEqualTo("FAKE");
        assertThat(capturedScanLog().getScanResult()).isEqualTo(ScanResult.TAMPERED);
        verify(hsmClient, never()).verifyNtag424(any(), any(), any(), any());
    }

    @Test
    void verifiesJsonCmacThroughHsmWhenKeyIsNotStoredInApplicationDatabase() {
        tag.setAesKey(null);
        when(hsmClient.isConfigured()).thenReturn(true);
        when(hsmClient.verifyCmac("04AABBCCDDEEFF", 1, request.cmac())).thenReturn(true);
        when(geoIp.lookup("8.8.8.8")).thenReturn(Optional.empty());
        when(logs.findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY)))
                .thenReturn(Optional.empty());

        VerifyResponse response = service.verify(request, "8.8.8.8");

        assertThat(response.status()).isEqualTo("REAL");
        verify(hsmClient).verifyCmac("04AABBCCDDEEFF", 1, request.cmac());
        verify(signatures, never()).matches("04AABBCCDDEEFF", 1, null, request.cmac());
        assertThat(tag.getLastScanCounter()).isEqualTo(1);
    }

    @Test
    void comparesSubmittedCoordinatesToLatestRealScanAndDoesNotAdvanceCounterOnTravelAnomaly() {
        when(signatures.matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac())).thenReturn(true);
        when(geoIp.lookup("8.8.8.8")).thenReturn(Optional.of(location));
        ScanLog previous = mock(ScanLog.class);
        when(previous.getLatitude()).thenReturn(-40.0);
        when(previous.getLongitude()).thenReturn(-50.0);
        when(previous.getScannedAt()).thenReturn(Instant.now().minusSeconds(60));
        when(logs.findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY)))
                .thenReturn(Optional.of(previous));

        VerifyResponse response = service.verify(request, "8.8.8.8");

        assertThat(response.status()).isEqualTo("FAKE");
        ScanLog anomaly = capturedScanLog();
        assertThat(anomaly.getScanResult()).isEqualTo(ScanResult.SPEED_ANOMALY);
        assertThat(anomaly.getGeoCountry()).isNull();
        assertThat(tag.getLastScanCounter()).isZero();
        verify(tags, never()).save(tag);
        verify(logs).findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY));
        verify(logs, never()).findTopByTagUidOrderByScannedAtDesc(any());
        InOrder order = inOrder(signatures, geoIp, logs);
        order.verify(signatures).matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac());
        order.verify(geoIp).lookup("8.8.8.8");
        order.verify(logs).findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY));
    }

    @Test
    void savesGeoIpEvidenceOnlyOnRealScanAndKeepsClientCoordinatesSeparate() {
        when(signatures.matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac())).thenReturn(true);
        when(geoIp.lookup("8.8.8.8")).thenReturn(Optional.of(location));
        when(logs.findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY)))
                .thenReturn(Optional.empty());

        VerifyResponse response = service.verify(request, "8.8.8.8");

        assertThat(response.status()).isEqualTo("REAL");
        ScanLog saved = capturedScanLog();
        assertThat(saved.getScanResult()).isEqualTo(ScanResult.REAL);
        assertThat(saved.getLatitude()).isEqualTo(40.0);
        assertThat(saved.getLongitude()).isEqualTo(50.0);
        assertThat(saved.getGeoCountry()).isEqualTo("Country");
        assertThat(saved.getGeoCountryIsoCode()).isEqualTo("CC");
        assertThat(saved.getGeoRegion()).isEqualTo("Region");
        assertThat(saved.getGeoCity()).isEqualTo("City");
        assertThat(saved.getGeoLatitude()).isEqualTo(40.0);
        assertThat(saved.getGeoLongitude()).isEqualTo(50.0);
        assertThat(tag.getLastScanCounter()).isEqualTo(1);
    }

    @Test
    void comparesSubmittedCoordinatesForTravelWhenGeoIpIsUnavailable() {
        when(signatures.matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac())).thenReturn(true);
        when(geoIp.lookup("8.8.8.8")).thenReturn(Optional.empty());
        ScanLog previous = mock(ScanLog.class);
        when(previous.getLatitude()).thenReturn(-40.0);
        when(previous.getLongitude()).thenReturn(-50.0);
        when(previous.getScannedAt()).thenReturn(Instant.now().minusSeconds(60));
        when(logs.findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY)))
                .thenReturn(Optional.of(previous));

        VerifyResponse response = service.verify(request, "8.8.8.8");

        assertThat(response.status()).isEqualTo("FAKE");
        assertThat(capturedScanLog().getScanResult()).isEqualTo(ScanResult.SPEED_ANOMALY);
        assertThat(capturedScanLog().getLatitude()).isEqualTo(40.0);
        assertThat(capturedScanLog().getGeoLatitude()).isNull();
        assertThat(tag.getLastScanCounter()).isZero();
    }

    @Test
    void usesSubmittedCoordinatesInsteadOfGeoIpForTravelDecision() {
        when(signatures.matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac())).thenReturn(true);
        when(geoIp.lookup("8.8.8.8")).thenReturn(Optional.of(
                new GeoIpLocation("Country", "CC", "Region", "City", -40.0, -50.0)));
        ScanLog previous = mock(ScanLog.class);
        when(previous.getLatitude()).thenReturn(40.0);
        when(previous.getLongitude()).thenReturn(50.0);
        when(previous.getScannedAt()).thenReturn(Instant.now().minusSeconds(60));
        when(logs.findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY)))
                .thenReturn(Optional.of(previous));

        VerifyResponse response = service.verify(request, "8.8.8.8");

        assertThat(response.status()).isEqualTo("REAL");
        assertThat(capturedScanLog().getScanResult()).isEqualTo(ScanResult.REAL);
        assertThat(tag.getLastScanCounter()).isEqualTo(1);
    }

    @Test
    void prefersPreviouslySharedDeviceGpsOverThatScansSubmittedCoordinates() {
        when(signatures.matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac())).thenReturn(true);
        ScanLog previous = mock(ScanLog.class);
        when(previous.getLatitude()).thenReturn(40.0);
        when(previous.getLongitude()).thenReturn(50.0);
        when(previous.getDeviceLatitude()).thenReturn(-40.0);
        when(previous.getDeviceLongitude()).thenReturn(-50.0);
        when(previous.getScannedAt()).thenReturn(Instant.now().minusSeconds(60));
        when(logs.findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY)))
                .thenReturn(Optional.of(previous));

        VerifyResponse response = service.verify(request, "8.8.8.8");

        assertThat(response.status()).isEqualTo("FAKE");
        assertThat(capturedScanLog().getScanResult()).isEqualTo(ScanResult.SPEED_ANOMALY);
        assertThat(tag.getLastScanCounter()).isZero();
    }

    @Test
    void publicNfcVerificationDoesNotUseGeoIpForImpossibleTravel() {
        when(signatures.matchesNtag424Sdm("04AABBCCDDEEFF", "000001", "mac-input",
                "0011223344556677", tag.getAesKey())).thenReturn(true);
        when(geoIp.lookup("8.8.8.8")).thenReturn(Optional.of(
                new GeoIpLocation("Country", "CC", "Region", "City", -40.0, -50.0)));

        VerifyResponse response = service.verifySdm(
                "04AABBCCDDEEFF", "000001", "0011223344556677", "mac-input", "8.8.8.8");

        assertThat(response.status()).isEqualTo("REAL");
        assertThat(capturedScanLog().getGeoLatitude()).isEqualTo(-40.0);
        assertThat(tag.getLastScanCounter()).isEqualTo(1);
        verify(logs, never()).findTopByTagUidAndScanResultInOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY));
    }

    @Test
    void scanLogDoesNotAttachGeoIpEvidenceToUnsuccessfulResults() {
        ScanLog tampered = new ScanLog("04AABBCCDDEEFF", null, null, "8.8.8.8",
                ScanResult.TAMPERED, 1, 1, location);

        assertThat(tampered.getGeoCountry()).isNull();
        assertThat(tampered.getGeoLatitude()).isNull();
        assertThat(tampered.getGeoLongitude()).isNull();
    }

    @Test
    void attachesUserSharedDeviceLocationOnlyToAnExistingRealScan() {
        ScanLog scan = mock(ScanLog.class);
        when(logs.findTopByTagUidAndReceivedCounterAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", 1, ScanResult.REAL)).thenReturn(Optional.of(scan));
        when(scan.getDeviceLatitude()).thenReturn(null);
        when(scan.getDeviceLongitude()).thenReturn(null);

        assertThat(service.attachDeviceLocation("04aabbccddeeff", 1, 40.4, 49.9)).isTrue();

        verify(scan).setDeviceLocation(40.4, 49.9);
        verify(logs).save(scan);
    }

    @Test
    void flagsNfcScanWhenSharedLocationShowsImpossibleTravel() {
        UUID currentId = UUID.randomUUID();
        ScanLog current = mock(ScanLog.class);
        when(current.getId()).thenReturn(currentId);
        when(current.getDeviceLatitude()).thenReturn(null);
        when(current.getDeviceLongitude()).thenReturn(null);
        when(logs.findTopByTagUidAndReceivedCounterAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", 2, ScanResult.REAL)).thenReturn(Optional.of(current));

        ScanLog previous = mock(ScanLog.class);
        when(previous.getLatitude()).thenReturn(40.4093);
        when(previous.getLongitude()).thenReturn(49.8671);
        when(previous.getScannedAt()).thenReturn(Instant.now().minusSeconds(60));
        when(logs.findTopByTagUidAndScanResultInAndIdNotOrderByScannedAtDesc(
                "04AABBCCDDEEFF", List.of(ScanResult.REAL, ScanResult.SPEED_ANOMALY), currentId))
                .thenReturn(Optional.of(previous));

        VerificationService.DeviceLocationResult result = service.attachDeviceLocationAndCheckTravel(
                "04AABBCCDDEEFF", 2, 37.1591, 38.7969);

        assertThat(result.attached()).isTrue();
        assertThat(result.travelAnomaly()).isTrue();
        assertThat(result.speedKmh()).isGreaterThan(1000.0);
        assertThat(result.distanceKm()).isGreaterThan(1.0);
        verify(current).setDeviceLocation(37.1591, 38.7969);
        verify(current).markSpeedAnomaly();
        verify(logs).save(current);
    }

    @Test
    void doesNotReplacePreviouslySharedDeviceLocation() {
        ScanLog scan = mock(ScanLog.class);
        when(logs.findTopByTagUidAndReceivedCounterAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", 1, ScanResult.REAL)).thenReturn(Optional.of(scan));
        when(scan.getDeviceLatitude()).thenReturn(40.4);

        assertThat(service.attachDeviceLocation("04AABBCCDDEEFF", 1, 41.0, 50.0)).isFalse();

        verify(scan, never()).setDeviceLocation(anyDouble(), anyDouble());
        verify(logs, never()).save(scan);
    }

    private ScanLog capturedScanLog() {
        ArgumentCaptor<ScanLog> captor = ArgumentCaptor.forClass(ScanLog.class);
        verify(logs).save(captor.capture());
        return captor.getValue();
    }
}
