package com.example.productauth.service;

import com.example.productauth.domain.ScanLog;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class FirebaseScanPublisher {

    private static final Logger log = LoggerFactory.getLogger(FirebaseScanPublisher.class);
    private static final String COLLECTION = "live_scan_events";
    private static final String ALERT_TOPIC = "admin_security_alerts";

    private final ObjectProvider<Firestore> firestoreProvider;
    private final ObjectProvider<FirebaseMessaging> messagingProvider;

    public FirebaseScanPublisher(ObjectProvider<Firestore> firestoreProvider,
            ObjectProvider<FirebaseMessaging> messagingProvider) {
        this.firestoreProvider = firestoreProvider;
        this.messagingProvider = messagingProvider;
    }

    public void publish(ScanLog scanLog, String productId) {
        Map<String, Object> data = new HashMap<>();
        data.put("tagUid", scanLog.getTagUid());
        data.put("scannedAt", scanLog.getScannedAt().toString());
        data.put("scanResult", scanLog.getScanResult().name());
        data.put("latitude", scanLog.getLatitude());
        data.put("longitude", scanLog.getLongitude());
        if (productId != null) {
            data.put("productId", productId);
        }

        Firestore firestore = firestoreProvider.getIfAvailable();
        if (firestore == null) {
            return;
        }

        try {
            ApiFuture<?> write = firestore.collection(COLLECTION).add(data);
            write.get();
        } catch (Exception exception) {
            log.warn("Could not mirror scan event to Firebase", exception);
        }

        if (scanLog.getScanResult().name().equals("TAMPERED")
                || scanLog.getScanResult().name().equals("REPLAY_ATTACK")
                || scanLog.getScanResult().name().equals("SPEED_ANOMALY")) {
            publishAlert(scanLog);
        }
    }

    private void publishAlert(ScanLog scanLog) {
        FirebaseMessaging messaging = messagingProvider.getIfAvailable();
        if (messaging == null) {
            return;
        }

        try {
            Message message = Message.builder()
                    .setTopic(ALERT_TOPIC)
                    .setNotification(Notification.builder()
                            .setTitle("Product security alert")
                            .setBody(scanLog.getScanResult().name() + " detected for " + scanLog.getTagUid())
                            .build())
                    .putData("tagUid", scanLog.getTagUid())
                    .putData("scanResult", scanLog.getScanResult().name())
                    .build();
            messaging.send(message);
        } catch (Exception exception) {
            log.warn("Could not publish Firebase security alert", exception);
        }
    }
}
