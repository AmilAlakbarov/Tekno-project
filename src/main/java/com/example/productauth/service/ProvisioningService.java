package com.example.productauth.service;

import com.example.productauth.api.dto.AdminDtos;
import com.example.productauth.domain.NfcTag;
import com.example.productauth.domain.Product;
import com.example.productauth.domain.ProvisioningBatch;
import com.example.productauth.repository.NfcTagRepository;
import com.example.productauth.repository.ProductRepository;
import com.example.productauth.repository.ProvisioningBatchRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProvisioningService {
    private final ProductRepository productRepository;
    private final NfcTagRepository nfcTagRepository;
    private final ProvisioningBatchRepository batchRepository;
    private final HsmClient hsmClient;

    public ProvisioningService(ProductRepository productRepository, NfcTagRepository nfcTagRepository,
            ProvisioningBatchRepository batchRepository, HsmClient hsmClient) {
        this.productRepository = productRepository;
        this.nfcTagRepository = nfcTagRepository;
        this.batchRepository = batchRepository;
        this.hsmClient = hsmClient;
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.ProductRow> products() {
        return productRepository.findAll().stream()
                .map(product -> new AdminDtos.ProductRow(product.getId(), product.getName(),
                        product.getManufacturer()))
                .toList();
    }

    @Transactional
    public AdminDtos.ProductRow createProduct(String name, String manufacturer) {
        if (name == null || name.isBlank() || manufacturer == null || manufacturer.isBlank()) {
            throw new IllegalArgumentException("Product name and manufacturer are required.");
        }
        Product product = new Product(UUID.randomUUID(), name.trim(), manufacturer.trim(), null);
        return new AdminDtos.ProductRow(productRepository.save(product).getId(), product.getName(),
                product.getManufacturer());
    }

    @Transactional
    public AdminDtos.ProvisioningResult importCsv(MultipartFile file, String createdBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty CSV file is required.");
        }
        int total = 0;
        int imported = 0;
        int duplicates = 0;
        int invalid = 0;
        Set<String> fileUids = new HashSet<>();
        ProvisioningBatch batch = new ProvisioningBatch(createdBy, 0, 0, 0, 0, "PROCESSING");
        batch = batchRepository.save(batch);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !hasRequiredColumns(header)) {
                throw new IllegalArgumentException("CSV must contain uid,aesKey,productId columns.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                total++;
                String[] columns = line.split(",", -1);
                if (columns.length < 3) {
                    invalid++;
                    continue;
                }
                String uid = columns[0].trim().toUpperCase(Locale.ROOT);
                String aesKey = columns[1].trim().toUpperCase(Locale.ROOT);
                String productId = columns[2].trim();
                if (!validUid(uid) || !validKey(aesKey) || !validProduct(productId)) {
                    invalid++;
                    continue;
                }
                if (!fileUids.add(uid) || nfcTagRepository.findByTagUid(uid).isPresent()) {
                    duplicates++;
                    continue;
                }
                Product product = productRepository.findById(UUID.fromString(productId))
                        .orElseThrow(() -> new IllegalArgumentException("Product was not found: " + productId));
                hsmClient.importKey(uid, aesKey);
                NfcTag tag = new NfcTag(UUID.randomUUID(), product, uid,
                        hsmClient.isConfigured() ? null : aesKey);
                if (columns.length > 3) tag.setDisplayName(blankToNull(columns[3]));
                if (columns.length > 4) tag.setDescription(blankToNull(columns[4]));
                if (columns.length > 5) tag.setImageUrl(blankToNull(columns[5]));
                tag.setProvisioningBatch(batch);
                nfcTagRepository.save(tag);
                imported++;
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the provisioning CSV.", exception);
        }
        String status = invalid == 0 && duplicates == 0 ? "IMPORTED" : "IMPORTED_WITH_WARNINGS";
        batch.setCounts(total, imported, duplicates, invalid, status);
        batch = batchRepository.save(batch);
        return AdminDtos.ProvisioningResult.from(batch);
    }

    private boolean hasRequiredColumns(String header) {
        Set<String> columns = Set.of(header.toLowerCase(Locale.ROOT).split(","));
        return columns.contains("uid") && columns.contains("aeskey") && columns.contains("productid");
    }

    private boolean validUid(String uid) {
        return uid.matches("(?i)[0-9a-f]{14}");
    }

    private boolean validKey(String key) {
        return key.matches("(?i)[0-9a-f]{32}");
    }

    private boolean validProduct(String productId) {
        try {
            return productRepository.existsById(UUID.fromString(productId));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
