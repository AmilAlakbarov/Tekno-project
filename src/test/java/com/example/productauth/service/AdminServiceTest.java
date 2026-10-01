package com.example.productauth.service;

import com.example.productauth.api.dto.AdminDtos;
import com.example.productauth.domain.NfcTag;
import com.example.productauth.domain.Product;
import com.example.productauth.domain.TagStatus;
import com.example.productauth.repository.NfcTagRepository;
import com.example.productauth.repository.ProductRepository;
import com.example.productauth.repository.ScanLogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceTest {
    private NfcTagRepository nfcTagRepository;
    private ScanLogRepository scanLogRepository;
    private ProductRepository productRepository;
    private HsmClient hsmClient;
    private AdminService service;

    @BeforeEach
    void setUp() {
        nfcTagRepository = mock(NfcTagRepository.class);
        scanLogRepository = mock(ScanLogRepository.class);
        productRepository = mock(ProductRepository.class);
        hsmClient = mock(HsmClient.class);
        service = new AdminService(nfcTagRepository, scanLogRepository, productRepository, hsmClient);
    }

    @Test
    void tagSearchUsesEmptyStringsForUnspecifiedTextFilters() {
        var pageable = PageRequest.of(0, 25);
        when(nfcTagRepository.searchTags("", "", null, "", "", null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.tags(pageable, new AdminService.TagFilters(null, null, null, null, null, null));

        verify(nfcTagRepository).searchTags(
                eq(""), eq(""), isNull(), eq(""), eq(""), isNull(), eq(pageable));
    }

    @Test
    void mapsDailyScanActivityFromRepository() {
        var day = LocalDate.of(2026, 9, 26);
        var activity = mock(ScanLogRepository.DailyScanActivity.class);
        when(activity.getDay()).thenReturn(day);
        when(activity.getTotal()).thenReturn(8L);
        when(activity.getVerified()).thenReturn(5L);
        when(activity.getFlagged()).thenReturn(3L);
        when(scanLogRepository.findDailyActivityForLastSevenDays()).thenReturn(List.of(activity));

        assertThat(service.scanActivity()).containsExactly(
                new AdminDtos.ActivityPoint(day, 8, 5, 3));
    }

    @Test
    void locationsPreferPreciseCoordinatesAndSuppressGeoIpFallback() {
        when(scanLogRepository.findTop100ByOrderByScannedAtDesc()).thenReturn(List.of(
                scan(ScanResult.REAL, null, null, 41.0, 49.0, 40.0, 50.0),
                scan(ScanResult.REAL, 37.0, 39.0, null, null, 40.0, 50.0),
                scan(ScanResult.REAL, null, null, null, null, 40.0, 50.0),
                scan(ScanResult.REAL, 37.0, null, null, null, 40.0, 50.0)));

        List<AdminDtos.LocationPoint> points = service.locations();

        assertThat(points).containsExactly(
                new AdminDtos.LocationPoint(41.0, 49.0, "DEVICE_GPS", Instant.EPOCH.toString()),
                new AdminDtos.LocationPoint(37.0, 39.0, "CLIENT_COORDINATES", Instant.EPOCH.toString()),
                new AdminDtos.LocationPoint(40.0, 50.0, "GEOIP", Instant.EPOCH.toString()));
    }

    @Test
    void bulkActionNormalizesUidsAndActivatesMatchingTags() {
        NfcTag first = tag("ABCDEFABCDEF01");
        NfcTag second = tag("11121314151617");
        when(nfcTagRepository.findByTagUidIn(List.of("ABCDEFABCDEF01", "11121314151617")))
                .thenReturn(List.of(first, second));

        int affected = service.bulkTags(
                List.of("abcdefabcdef01", "11121314151617"), "activate");

        assertThat(affected).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(TagStatus.ACTIVE);
        assertThat(second.getStatus()).isEqualTo(TagStatus.ACTIVE);
    }

    @Test
    void bulkDeleteRemovesHsmKeysBeforeTagRows() {
        NfcTag first = tag("01020304050607");
        NfcTag second = tag("11121314151617");
        when(nfcTagRepository.findByTagUidIn(List.of("01020304050607", "11121314151617")))
                .thenReturn(List.of(first, second));

        assertThat(service.bulkTags(List.of(first.getTagUid(), second.getTagUid()), "DELETE"))
                .isEqualTo(2);

        var order = inOrder(hsmClient, nfcTagRepository);
        order.verify(hsmClient).deleteKey(first.getTagUid());
        order.verify(hsmClient).deleteKey(second.getTagUid());
        order.verify(nfcTagRepository).deleteAll(List.of(first, second));
        order.verify(nfcTagRepository).flush();
    }

    @Test
    void bulkActionRejectsInvalidUidAndUnknownTags() {
        assertThatThrownBy(() -> service.bulkTags(List.of("not-a-uid"), "REVOKE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tag UID");

        when(nfcTagRepository.findByTagUidIn(List.of("01020304050607")))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.bulkTags(List.of("01020304050607"), "REVOKE"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Tags were not found");
    }

    @Test
    void deletingProductRemovesHsmKeysAndTagsBeforeProduct() {
        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, "Widget", "Acme", null);
        NfcTag tag = tag("01020304050607");
        tag.setProduct(product);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(nfcTagRepository.findByProductId(productId)).thenReturn(List.of(tag));

        service.deleteProduct(productId);

        var order = inOrder(hsmClient, nfcTagRepository, productRepository);
        order.verify(hsmClient).deleteKey(tag.getTagUid());
        order.verify(nfcTagRepository).deleteAll(List.of(tag));
        order.verify(nfcTagRepository).flush();
        order.verify(productRepository).delete(product);
    }

    private NfcTag tag(String uid) {
        Product product = new Product(UUID.randomUUID(), "Widget", "Acme", null);
        return new NfcTag(UUID.randomUUID(), product, uid, "0123456789ABCDEF0123456789ABCDEF");
    }

    private ScanLog scan(ScanResult result, Double latitude, Double longitude,
            Double deviceLatitude, Double deviceLongitude, Double geoLatitude, Double geoLongitude) {
        ScanLog log = mock(ScanLog.class);
        when(log.getScanResult()).thenReturn(result);
        when(log.getLatitude()).thenReturn(latitude);
        when(log.getLongitude()).thenReturn(longitude);
        when(log.getDeviceLatitude()).thenReturn(deviceLatitude);
        when(log.getDeviceLongitude()).thenReturn(deviceLongitude);
        when(log.getGeoLatitude()).thenReturn(geoLatitude);
        when(log.getGeoLongitude()).thenReturn(geoLongitude);
        when(log.getScannedAt()).thenReturn(Instant.EPOCH);
        return log;
    }
}
