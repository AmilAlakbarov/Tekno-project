package com.example.productauth.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.productauth.api.dto.VerifyResponse;
import com.example.productauth.service.VerificationService;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/nfc")
public class NfcScanController {

    private static final Logger log = LoggerFactory.getLogger(NfcScanController.class);
    private final VerificationService verificationService;

    public NfcScanController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping(path = {"", "/v1/verify"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> receiveNfcMessage(
            @RequestParam Map<String, String> parameters, HttpServletRequest request) {
        log.info("NFC SDM request received: parameters={}", parameters.keySet());

        String uid = parameters.get("uid");
        String counter = parameters.get("ctr");
        String cmac = parameters.get("cmac");
        if (uid == null || counter == null || cmac == null
                || !uid.matches("(?i)[0-9a-f]{14}")
                || !counter.matches("(?i)[0-9a-f]{6}")
                || !cmac.matches("(?i)[0-9a-f]{16}")) {
            return Map.of(
                    "status", "FAKE",
                    "message", "NFC authentication data is incomplete or invalid.",
                    "receivedAt", Instant.now().toString(),
                    "parameterNames", parameters.keySet());
        }

        String query = request.getQueryString();
        int cmacIndex = query == null ? -1 : query.toLowerCase().indexOf("cmac=");
        if (cmacIndex < 0) {
            return Map.of(
                    "status", "FAKE",
                    "message", "NFC authentication data is invalid.",
                    "receivedAt", Instant.now().toString(),
                    "parameterNames", parameters.keySet());
        }

        String macInput = request.getRequestURL().toString() + "?" + query.substring(0, cmacIndex + 5);
        VerifyResponse result = verificationService.verifySdm(uid, counter, cmac, macInput);
        return Map.of(
                "status", result.status(),
                "message", result.message(),
                "product", result.product() == null ? Map.of() : result.product(),
                "receivedAt", Instant.now().toString());
    }
}
