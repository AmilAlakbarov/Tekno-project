package com.example.productauth.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String manufacturer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dpp_data", columnDefinition = "jsonb")
    private JsonNode dppData;

    protected Product() {
    }

    public Product(UUID id, String name, String manufacturer, JsonNode dppData) {
        this.id = id;
        this.name = name;
        this.manufacturer = manufacturer;
        this.dppData = dppData;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public JsonNode getDppData() {
        return dppData;
    }

    public void setDppData(JsonNode dppData) {
        this.dppData = dppData;
    }
}
