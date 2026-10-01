package com.example.productauth.service;

import com.example.productauth.api.dto.AdminDtos;
import com.example.productauth.domain.NfcTag;
import com.example.productauth.domain.Product;
import com.example.productauth.domain.ProvisioningBatch;
import com.example.productauth.repository.NfcTagRepository;
import com.example.productauth.repository.ProductRepository;
import com.example.productauth.repository.ProvisioningBatchRepository;
import jakarta.persistence.EntityNotFoundException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                        product.getManufacturer(), nfcTagRepository.countByProductId(product.getId())))
                .toList();
    }

    @Transactional
    public AdminDtos.ProductRow createProduct(String name, String manufacturer) {
        if (name == null || name.isBlank() || manufacturer == null || manufacturer.isBlank()) {
            throw new IllegalArgumentException("Product name and manufacturer are required.");
        }
        Product product = new Product(UUID.randomUUID(), name.trim(), manufacturer.trim(), null);
        return new AdminDtos.ProductRow(productRepository.save(product).getId(), product.getName(),
                product.getManufacturer(), 0);
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
        try (CSVParser parser = CSVParser.parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).get())) {
            Map<String, Integer> columns = parser.getHeaderMap().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> entry.getKey().trim().toLowerCase(Locale.ROOT), Map.Entry::getValue));
            if (!columns.keySet().containsAll(Set.of("uid", "aeskey", "productid"))) {
                throw new IllegalArgumentException("CSV must contain uid,aesKey,productId columns.");
            }
            for (CSVRecord record : parser) {
                total++;
                String uid = field(record, columns, "uid").toUpperCase(Locale.ROOT);
                String aesKey = field(record, columns, "aeskey").toUpperCase(Locale.ROOT);
                String productId = field(record, columns, "productid");
                if (!validUid(uid) || !validKey(aesKey) || !validProduct(productId)) {
                    invalid++;
                    continue;
                }
                // Re-importing a CSV also restores an HSM key after a development
                // service reset, without creating a duplicate tag row.
                hsmClient.importKey(uid, aesKey);
                if (!fileUids.add(uid) || nfcTagRepository.existsByTagUid(uid)) {
                    duplicates++;
                    continue;
                }
                Product product = productRepository.findById(UUID.fromString(productId))
                        .orElseThrow(() -> new IllegalArgumentException("Product was not found: " + productId));
                NfcTag tag = new NfcTag(UUID.randomUUID(), product, uid,
                        hsmClient.isConfigured() ? null : aesKey);
                tag.setDisplayName(blankToNull(field(record, columns, "displayname")));
                tag.setDescription(blankToNull(field(record, columns, "description")));
                tag.setImageUrl(blankToNull(field(record, columns, "imageurl")));
                tag.setProvisioningBatch(batch);
                nfcTagRepository.saveAndFlush(tag);
                imported++;
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the provisioning CSV.", exception);
        } catch (IllegalArgumentException exception) {
            throw exception;
        }
        String status = invalid == 0 && duplicates == 0 ? "IMPORTED" : "IMPORTED_WITH_WARNINGS";
        batch.setCounts(total, imported, duplicates, invalid, status);
        batch = batchRepository.save(batch);
        return AdminDtos.ProvisioningResult.from(batch);
    }

    @Transactional(readOnly = true)
    public byte[] exportBatchCsv(UUID batchId) {
        if (!hsmClient.isConfigured()) {
            throw new IllegalStateException("Configure the HSM before exporting provisioning AES keys.");
        }
        ProvisioningBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Provisioning batch not found."));
        List<NfcTag> tags = nfcTagRepository.findByProvisioningBatchIdOrderByTagUidAsc(batch.getId());
        if (tags.isEmpty()) {
            throw new IllegalStateException("This provisioning batch contains no registered tags to export.");
        }
        Map<String, String> hsmKeys = hsmClient.exportKeys(tags.stream().map(NfcTag::getTagUid).toList());
        try (StringWriter output = new StringWriter();
                CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT.builder()
                        .setHeader("uid", "aesKey", "productId", "displayName", "description", "imageUrl").get())) {
            for (NfcTag tag : tags) {
                String key = hsmKeys.get(tag.getTagUid());
                if (key == null || !key.matches("(?i)[0-9a-f]{32}")) {
                    throw new IllegalStateException("A valid AES key is unavailable for a tag in this batch.");
                }
                printer.printRecord(tag.getTagUid(), key.toUpperCase(Locale.ROOT), tag.getProduct().getId(),
                        tag.getDisplayName(), tag.getDescription(), tag.getImageUrl());
            }
            printer.flush();
            return output.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create the provisioning key CSV.", exception);
        }
    }

    private String field(CSVRecord record, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        return index == null || index >= record.size() ? "" : record.get(index).trim();
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
