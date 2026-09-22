package com.example.productauth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureVerificationServiceTest {

    private final SignatureVerificationService service = new SignatureVerificationService();

    @Test
    void verifiesPrototypeCmacVector() {
        String uid = "04A1B2C3D4E5F6";
        String key = "000102030405060708090A0B0C0D0E0F";
        String expected = "B4CF78EAF9793BF9";

        assertThat(service.matches(uid, 66, key, expected)).isTrue();
    }

    @Test
    void rejectsWrongCmac() {
        assertThat(service.matches("04A1B2C3D4E5F6", 66,
                "000102030405060708090A0B0C0D0E0F", "0000000000000000")).isFalse();
    }
}
