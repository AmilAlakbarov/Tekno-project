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
    private final VerificationService service = new VerificationService(tags, logs, signatures, hsmClient, geoIp);
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
        verify(logs, never()).findTopByTagUidAndScanResultOrderByScannedAtDesc(any(), any());
        assertThat(capturedScanLog().getScanResult()).isEqualTo(ScanResult.TAMPERED);
        assertThat(capturedScanLog().getGeoCountry()).isNull();
        assertThat(tag.getLastScanCounter()).isZero();
    }

    @Test
    void comparesSubmittedCoordinatesToLatestRealScanAndDoesNotAdvanceCounterOnTravelAnomaly() {
        when(signatures.matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac())).thenReturn(true);
        when(geoIp.lookup("8.8.8.8")).thenReturn(Optional.of(location));
        ScanLog previous = mock(ScanLog.class);
        when(previous.getLatitude()).thenReturn(-40.0);
        when(previous.getLongitude()).thenReturn(-50.0);
        when(previous.getScannedAt()).thenReturn(Instant.now().minusSeconds(60));
        when(logs.findTopByTagUidAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", ScanResult.REAL)).thenReturn(Optional.of(previous));

        VerifyResponse response = service.verify(request, "8.8.8.8");

        assertThat(response.status()).isEqualTo("FAKE");
        ScanLog anomaly = capturedScanLog();
        assertThat(anomaly.getScanResult()).isEqualTo(ScanResult.SPEED_ANOMALY);
        assertThat(anomaly.getGeoCountry()).isNull();
        assertThat(tag.getLastScanCounter()).isZero();
        verify(tags, never()).save(tag);
        verify(logs).findTopByTagUidAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", ScanResult.REAL);
        verify(logs, never()).findTopByTagUidOrderByScannedAtDesc(any());
        InOrder order = inOrder(signatures, geoIp, logs);
        order.verify(signatures).matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac());
        order.verify(geoIp).lookup("8.8.8.8");
        order.verify(logs).findTopByTagUidAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", ScanResult.REAL);
    }

    @Test
    void savesGeoIpEvidenceOnlyOnRealScanAndKeepsClientCoordinatesSeparate() {
        when(signatures.matches("04AABBCCDDEEFF", 1, tag.getAesKey(), request.cmac())).thenReturn(true);
        when(geoIp.lookup("8.8.8.8")).thenReturn(Optional.of(location));
        when(logs.findTopByTagUidAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", ScanResult.REAL)).thenReturn(Optional.empty());

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
        when(logs.findTopByTagUidAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", ScanResult.REAL)).thenReturn(Optional.of(previous));

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
        when(logs.findTopByTagUidAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", ScanResult.REAL)).thenReturn(Optional.of(previous));

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
        when(logs.findTopByTagUidAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", ScanResult.REAL)).thenReturn(Optional.of(previous));

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
        verify(logs, never()).findTopByTagUidAndScanResultOrderByScannedAtDesc(
                "04AABBCCDDEEFF", ScanResult.REAL);
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
