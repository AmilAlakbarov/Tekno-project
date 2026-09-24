package com.example.productauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provisioning_batches")
public class ProvisioningBatch {
    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(nullable = false)
    private String status;

    protected ProvisioningBatch() {
    }

    public ProvisioningBatch(String createdBy, int totalRows, int importedRows,
            int duplicateRows, int invalidRows, String status) {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.createdBy = createdBy;
        this.totalRows = totalRows;
        this.importedRows = importedRows;
        this.duplicateRows = duplicateRows;
        this.invalidRows = invalidRows;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public int getDuplicateRows() {
        return duplicateRows;
    }

    public int getInvalidRows() {
        return invalidRows;
    }

    public String getStatus() {
        return status;
    }

    public void setCounts(int totalRows, int importedRows, int duplicateRows,
            int invalidRows, String status) {
        this.totalRows = totalRows;
        this.importedRows = importedRows;
        this.duplicateRows = duplicateRows;
        this.invalidRows = invalidRows;
        this.status = status;
    }
}
