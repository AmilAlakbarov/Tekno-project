package com.example.productauth.config;

import com.example.productauth.domain.NfcTag;
import com.example.productauth.repository.NfcTagRepository;
import com.example.productauth.repository.ScanLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "database.reset-on-startup", havingValue = "true")
public class DemoDatabaseReset {

    private static final String REPLAY_TEST_UID = "042166521F1E90";

    private final NfcTagRepository nfcTagRepository;
    private final ScanLogRepository scanLogRepository;

    public DemoDatabaseReset(NfcTagRepository nfcTagRepository, ScanLogRepository scanLogRepository) {
        this.nfcTagRepository = nfcTagRepository;
        this.scanLogRepository = scanLogRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resetDemoState() {
        scanLogRepository.deleteAllInBatch();
        for (NfcTag tag : nfcTagRepository.findAll()) {
            tag.setLastScanCounter(REPLAY_TEST_UID.equals(tag.getTagUid()) ? 2 : 0);
        }
        nfcTagRepository.flush();
    }
}
