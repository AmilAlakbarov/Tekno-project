package com.example.productauth.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import com.example.productauth.api.dto.ProductSummary;
import com.example.productauth.api.dto.VerifyResponse;
import com.example.productauth.service.VerificationService;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/nfc")
public class NfcScanController {

    private static final Logger log = LoggerFactory.getLogger(NfcScanController.class);
    private final VerificationService verificationService;
    private final String publicBaseUrl;

    public NfcScanController(VerificationService verificationService,
            @Value("${app.public-base-url:}") String publicBaseUrl) {
        this.verificationService = verificationService;
        this.publicBaseUrl = publicBaseUrl;
    }

    @GetMapping(path = {"", "/v1/verify"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> receiveNfcMessage(
            @RequestParam Map<String, String> parameters, HttpServletRequest request) {
        log.info("NFC SDM request received: parameters={}", parameters.keySet());

        String uid = parameters.get("uid");
        String counter = parameters.get("ctr");
        String cmac = parameters.get("cmac");
        if (uid == null || counter == null || cmac == null
                || !uid.matches("(?i)[0-9a-f]{14}")
                || !counter.matches("(?i)[0-9a-f]{6}")
                || !cmac.matches("(?i)[0-9a-f]{16}")) {
            return page("FAKE", "NFC authentication data is incomplete or invalid.",
                    null, null, null, null);
        }

        String query = request.getQueryString();
        int cmacIndex = query == null ? -1 : query.toLowerCase().indexOf("cmac=");
        if (cmacIndex < 0) {
            return page("FAKE", "NFC authentication data is invalid.",
                    uid, counter, null, null);
        }

        String requestBaseUrl = publicBaseUrl.isBlank()
                ? request.getRequestURL().toString()
                : publicBaseUrl + request.getRequestURI();
        String macInput = requestBaseUrl + "?" + query.substring(0, cmacIndex + 5);
        VerifyResponse result = verificationService.verifySdm(uid, counter, cmac, macInput);
        return page(result.status(), result.message(), uid, counter, cmac, result.product());
    }

    private ResponseEntity<String> page(String status, String message, String uid, String counter,
            String cmac, ProductSummary product) {
        boolean authentic = "REAL".equalsIgnoreCase(status);
        boolean replay = "REPLAY_ATTACK".equalsIgnoreCase(status)
                || (message != null && message.toLowerCase(Locale.ROOT).contains("replay"));
        String title = authentic ? "Authentic product" : replay ? "Replay detected" : "Verification failed";
        String safeMessage = escape(message);
        String productName = product == null ? "Product not verified" : escape(product.name());
        String manufacturer = product == null ? "Authentication did not complete successfully"
                : escape(product.manufacturer());
        String details = uid == null ? "Scan data was not complete enough to display."
                : "UID " + escape(uid.toUpperCase(Locale.ROOT))
                        + (counter == null ? "" : "  ·  Counter " + escape(counter.toUpperCase(Locale.ROOT)));
        String macDetail = cmac == null ? "" : "<span>CMAC " + escape(cmac.toUpperCase(Locale.ROOT)) + "</span>";
        String icon = authentic
                ? "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M20 6 9 17l-5-5\"/></svg>"
                : replay
                ? "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M3 12a9 9 0 1 0 3-6.7\"/><path d=\"M3 4v6h6\"/><path d=\"M12 7v5l3 2\"/></svg>"
                : "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M18 6 6 18M6 6l12 12\"/></svg>";
        String accentBackground = authentic ? "#e6f5f1" : replay ? "#fff7df" : "#fff0ef";
        String accentColor = authentic ? "#087f6b" : replay ? "#a86b00" : "#c84b45";
        String eyebrow = authentic ? "Verified" : replay ? "Security warning" : "Not verified";

        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s | Authentichain</title>
                  <style>
                    :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, -apple-system, sans-serif; }
                    * { box-sizing: border-box; }
                    body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 24px;
                      background: #f3f6f8; color: #17232b; }
                    .card { width: min(100%%, 480px); overflow: hidden; border: 1px solid #dce5e8;
                      border-radius: 24px; background: #fff; box-shadow: 0 20px 55px #17323d18; }
                    .top { padding: 24px 28px; display: flex; align-items: center; gap: 12px;
                      border-bottom: 1px solid #edf1f2; }
                    .brand-mark { width: 38px; height: 38px; display: grid; place-items: center;
                      border-radius: 12px; background: #e6f5f1; color: #087f6b; }
                    .brand-mark svg { width: 22px; height: 22px; fill: none; stroke: currentColor;
                      stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
                    .brand { font-weight: 750; letter-spacing: -.02em; font-size: 18px; }
                    .team { margin-left: auto; color: #71818a; font-size: 12px; }
                    .body { padding: 40px 28px 32px; text-align: center; }
                    .icon { width: 76px; height: 76px; display: grid; place-items: center; margin: 0 auto 24px;
                      border-radius: 50%%; background: %s; color: %s; }
                    .icon svg { width: 38px; height: 38px; fill: none; stroke: currentColor;
                      stroke-width: 2.4; stroke-linecap: round; stroke-linejoin: round; }
                    .eyebrow { margin: 0 0 8px; color: %s; font-size: 12px; font-weight: 800;
                      letter-spacing: .12em; text-transform: uppercase; }
                    h1 { margin: 0; font-size: clamp(28px, 7vw, 38px); letter-spacing: -.045em; }
                    .message { margin: 14px auto 0; max-width: 350px; color: #60717a; line-height: 1.55; }
                    .product { margin-top: 28px; padding: 18px; border-radius: 16px; text-align: left;
                      background: #f7f9fa; border: 1px solid #edf1f2; }
                    .product-label { color: #7a8990; font-size: 11px; font-weight: 800; letter-spacing: .1em;
                      text-transform: uppercase; }
                    .product-name { margin-top: 6px; font-size: 18px; font-weight: 750; }
                    .manufacturer { margin-top: 4px; color: #65767e; font-size: 14px; }
                    .meta { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px 16px;
                      margin-top: 24px; color: #819098; font: 11px ui-monospace, SFMono-Regular, monospace; }
                    .bottom { padding: 18px 28px; background: #fbfcfc; border-top: 1px solid #edf1f2;
                      color: #91a0a6; text-align: center; font-size: 12px; }
                    .bottom strong { color: #5f7078; }
                  </style>
                </head>
                <body>
                  <main class="card">
                    <header class="top">
                      <div class="brand-mark">
                        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 5 6v5c0 4.6 2.9 8.4 7 10 4.1-1.6 7-5.4 7-10V6l-7-3Z"/><path d="m9 12 2 2 4-4"/></svg>
                      </div>
                      <div class="brand">Authentichain</div>
                      <div class="team">Product verification</div>
                    </header>
                    <section class="body">
                      <div class="icon" style="background:%s;color:%s">%s</div>
                      <p class="eyebrow">%s</p>
                      <h1>%s</h1>
                      <p class="message">%s</p>
                      <div class="product">
                        <div class="product-label">Scanned item</div>
                        <div class="product-name">%s</div>
                        <div class="manufacturer">%s</div>
                      </div>
                      <div class="meta"><span>%s</span>%s</div>
                    </section>
                    <footer class="bottom"><strong>Authentichain Team</strong> · Secure NFC identity</footer>
                  </main>
                </body>
                </html>
                """.formatted(title, accentBackground, accentColor, accentColor, accentBackground,
                accentColor, icon, eyebrow,
                title, safeMessage, productName, manufacturer, details, macDetail);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
