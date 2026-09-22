package com.example.productauth.api.dto;

public record VerifyResponse(String status, String message, ProductSummary product) {

    public static VerifyResponse fake(String message) {
        return new VerifyResponse("FAKE", message, null);
    }

    public static VerifyResponse real(ProductSummary product) {
        return new VerifyResponse("REAL", "Product is authentic.", product);
    }
}
