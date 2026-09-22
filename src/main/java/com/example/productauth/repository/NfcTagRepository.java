package com.example.productauth.repository;

import com.example.productauth.domain.NfcTag;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface NfcTagRepository extends JpaRepository<NfcTag, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NfcTag> findByTagUid(String tagUid);
}
