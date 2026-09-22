package com.example.productauth.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerifyRequest(
        @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{14}", message = "uid must contain exactly 14 hexadecimal characters") String uid,

        @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{1,6}", message = "ctr must contain up to 6 hexadecimal characters") String ctr,

        @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{16}", message = "cmac must contain exactly 16 hexadecimal characters") String cmac,

        @NotNull @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double latitude,

        @NotNull @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double longitude) {
}
