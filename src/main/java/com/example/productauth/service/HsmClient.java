package com.example.productauth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class HsmClient {
    private final RestClient client;
    private final String token;
    private final boolean configured;

    public HsmClient(@Value("${app.hsm.base-url:}") String baseUrl,
            @Value("${app.hsm.service-token:}") String token) {
        this.configured = baseUrl != null && !baseUrl.isBlank();
        this.token = token;
        this.client = RestClient.builder().baseUrl(this.configured ? baseUrl : "http://127.0.0.1").build();
    }

    public boolean isConfigured() {
        return configured;
    }

    public void importKey(String uid, String aesKey) {
        if (!configured) {
            return;
        }
        client.post().uri("/v1/keys/import")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new KeyImportRequest(uid, aesKey))
                .retrieve().toBodilessEntity();
    }

    public boolean verifyNtag424(String uid, String counterHex, String macInput, String incomingCmac) {
        if (!configured) {
            return false;
        }
        VerifyResponse response = client.post().uri("/v1/ntag424/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new NtagVerifyRequest(uid, counterHex, macInput, incomingCmac))
                .retrieve().body(VerifyResponse.class);
        return response != null && response.valid();
    }

    private record KeyImportRequest(String uid, String aes_key) {
    }

    private record NtagVerifyRequest(String uid, String counter_hex, String mac_input, String incoming_cmac) {
    }

    private record VerifyResponse(String uid, boolean valid) {
    }
}
