package com.example.productauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "nfc_tags")
public class NfcTag {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "tag_uid", nullable = false, unique = true, length = 14)
    private String tagUid;

    @Column(name = "aes_key", nullable = false, length = 32)
    private String aesKey;

    @Column(name = "last_scan_counter", nullable = false)
    private int lastScanCounter = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TagStatus status = TagStatus.ACTIVE;

    protected NfcTag() {
    }

    public NfcTag(UUID id, Product product, String tagUid, String aesKey) {
        this.id = id;
        this.product = product;
        this.tagUid = tagUid;
        this.aesKey = aesKey;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getTagUid() {
        return tagUid;
    }

    public void setTagUid(String tagUid) {
        this.tagUid = tagUid;
    }

    public String getAesKey() {
        return aesKey;
    }

    public void setAesKey(String aesKey) {
        this.aesKey = aesKey;
    }

    public int getLastScanCounter() {
        return lastScanCounter;
    }

    public void setLastScanCounter(int lastScanCounter) {
        this.lastScanCounter = lastScanCounter;
    }

    public TagStatus getStatus() {
        return status;
    }

    public void setStatus(TagStatus status) {
        this.status = status;
    }
}
