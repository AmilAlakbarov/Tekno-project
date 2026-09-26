package com.example.productauth.domain;

public record GeoIpLocation(String country, String countryIsoCode, String region, String city,
        Double latitude, Double longitude) {
}
