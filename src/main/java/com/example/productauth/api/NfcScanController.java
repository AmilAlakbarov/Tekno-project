package com.example.productauth.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/nfc")
public class NfcScanController {

    private static final Logger log = LoggerFactory.getLogger(NfcScanController.class);

    @GetMapping(path = {"", "/v1/verify"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> receiveNfcMessage(
            @RequestParam Map<String, String> parameters) {
        log.info("NFC SDM request received: parameters={}", parameters.keySet());

        return Map.of(
                "status", "RECEIVED",
                "message", "NFC message reached the backend.",
                "receivedAt", Instant.now().toString(),
                "parameterNames", parameters.keySet());
    }
}
