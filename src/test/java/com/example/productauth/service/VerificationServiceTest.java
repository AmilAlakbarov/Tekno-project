package com.example.productauth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationServiceTest {

    @Test
    void parsesHexadecimalCounter() {
        assertThat(VerificationService.parseCounter("000042")).isEqualTo(66);
    }

    @Test
    void calculatesZeroDistanceForSameCoordinates() {
        assertThat(VerificationService.haversineKm(40.5858, 49.6317, 40.5858, 49.6317))
                .isLessThan(0.000001);
    }

    @Test
    void detectsLongDistanceAsKilometers() {
        assertThat(VerificationService.haversineKm(0, 0, 0, 1))
                .isBetween(111.0, 112.0);
    }
}
