package com.example.productauth.repository;

import com.example.productauth.domain.ProvisioningBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProvisioningBatchRepository extends JpaRepository<ProvisioningBatch, UUID> {
}
