package com.example.productauth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoIpServiceTest {

    @Test
    void disabledServiceReturnsNoLocation() {
        GeoIpService service = new GeoIpService("");

        assertThat(service.lookup("8.8.8.8")).isEmpty();
    }

    @Test
    void configuredMissingDatabaseFailsInsteadOfSilentlyDisablingGeoIp() {
        assertThatThrownBy(() -> new GeoIpService("missing-geoip-city-test.mmdb"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not open the configured local GeoIP City database");
    }

    @Test
    void classifiesPrivateReservedAndPublicAddressesWithoutResolvingHostnames() {
        assertThat(GeoIpService.isPublicAddress(GeoIpService.parseLiteralAddress("127.0.0.1").getAddress()))
                .isFalse();
        assertThat(GeoIpService.isPublicAddress(GeoIpService.parseLiteralAddress("10.10.0.1").getAddress()))
                .isFalse();
        assertThat(GeoIpService.isPublicAddress(GeoIpService.parseLiteralAddress("192.0.2.1").getAddress()))
                .isFalse();
        assertThat(GeoIpService.isPublicAddress(GeoIpService.parseLiteralAddress("2001:db8::1").getAddress()))
                .isFalse();
        assertThat(GeoIpService.isPublicAddress(GeoIpService.parseLiteralAddress("8.8.8.8").getAddress()))
                .isTrue();
        assertThat(GeoIpService.isPublicAddress(
                GeoIpService.parseLiteralAddress("2606:4700:4700::1111").getAddress())).isTrue();
        assertThatThrownBy(() -> GeoIpService.parseLiteralAddress("example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
