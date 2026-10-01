package com.example.productauth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    public void deleteKey(String uid) {
        if (!configured) {
            return;
        }
        client.post().uri("/v1/keys/delete")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new KeyDeleteRequest(uid))
                .retrieve().toBodilessEntity();
    }

    public Map<String, String> exportKeys(List<String> uids) {
        if (!configured) {
            throw new IllegalStateException("HSM key export is unavailable because the HSM is not configured.");
        }
        Map<String, String> keys = new HashMap<>();
        for (int start = 0; start < uids.size(); start += 1000) {
            List<String> batch = uids.subList(start, Math.min(start + 1000, uids.size()));
            KeyExportResponse response = client.post().uri("/v1/keys/export")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(new KeyExportRequest(batch))
                    .retrieve().body(KeyExportResponse.class);
            if (response == null || response.keys() == null || response.keys().size() != batch.size()) {
                throw new IllegalStateException("HSM did not return a key for every UID in the provisioning batch.");
            }
            for (KeyRecord key : response.keys()) {
                if (keys.putIfAbsent(key.uid(), key.aesKey()) != null) {
                    throw new IllegalStateException("HSM returned a duplicate UID in the provisioning key export.");
                }
            }
        }
        if (!keys.keySet().equals(Set.copyOf(uids))
                || keys.values().stream().anyMatch(key -> key == null || !key.matches("(?i)[0-9a-f]{32}"))) {
            throw new IllegalStateException("HSM returned an invalid or incomplete provisioning key export.");
        }
        return keys;
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

    public boolean verifyCmac(String uid, int counter, String incomingCmac) {
        if (!configured) {
            return false;
        }
        String messageHex = uid + String.format("%06X", counter);
        VerifyResponse response = client.post().uri("/v1/cmac/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CmacVerifyRequest(uid, messageHex, incomingCmac))
                .retrieve().body(VerifyResponse.class);
        return response != null && response.valid();
    }

    private record KeyImportRequest(String uid, String aes_key) {
    }

    private record KeyDeleteRequest(String uid) {
    }

    private record KeyExportRequest(List<String> uids) {
    }

    private record KeyRecord(String uid, String aesKey) {
    }

    private record KeyExportResponse(List<KeyRecord> keys) {
    }

    private record NtagVerifyRequest(String uid, String counter_hex, String mac_input, String incoming_cmac) {
    }

    private record CmacVerifyRequest(String uid, String message_hex, String cmac) {
    }

    private record VerifyResponse(String uid, boolean valid) {
    }
}
