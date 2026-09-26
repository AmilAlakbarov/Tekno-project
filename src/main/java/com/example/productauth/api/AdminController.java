package com.example.productauth.api;

import com.example.productauth.api.dto.AdminDtos;
import com.example.productauth.domain.TagStatus;
import com.example.productauth.service.AdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;
import com.example.productauth.service.ProvisioningService;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final ProvisioningService provisioningService;

    public AdminController(AdminService adminService, ProvisioningService provisioningService) {
        this.adminService = adminService;
        this.provisioningService = provisioningService;
    }

    @GetMapping("/overview")
    public AdminDtos.Overview overview() {
        return adminService.overview();
    }

    @GetMapping("/tags")
    public Page<AdminDtos.TagRow> tags(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String uid,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "") String productName,
            @RequestParam(defaultValue = "") String manufacturer,
            @RequestParam(required = false) TagStatus status) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return adminService.tags(PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "lastScanCounter")),
                new AdminService.TagFilters(uid, q, productId, productName, manufacturer, status));
    }

    @GetMapping("/scans")
    public List<AdminDtos.ScanRow> scans() {
        return adminService.scans();
    }

    @GetMapping("/security-events")
    public List<AdminDtos.ScanRow> securityEvents() {
        return adminService.securityEvents();
    }

    @GetMapping("/scans/locations")
    public List<AdminDtos.LocationPoint> locations() {
        return adminService.locations();
    }

    @PostMapping("/tags/{uid}/revoke")
    public ResponseEntity<AdminDtos.TagRow> revoke(@PathVariable String uid) {
        return ResponseEntity.ok(adminService.revoke(uid));
    }

    @PostMapping("/tags/{uid}/activate")
    public ResponseEntity<AdminDtos.TagRow> activate(@PathVariable String uid) {
        return ResponseEntity.ok(adminService.activate(uid));
    }

    @DeleteMapping("/tags/{uid}")
    public ResponseEntity<Void> deleteTag(@PathVariable String uid) {
        adminService.deleteTag(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tags/bulk")
    public AdminDtos.BulkActionResult bulkTags(@Valid @RequestBody BulkTagRequest request) {
        return new AdminDtos.BulkActionResult(adminService.bulkTags(request.uids(), request.action()));
    }

    @GetMapping("/products")
    public List<AdminDtos.ProductRow> products() {
        return provisioningService.products();
    }

    @PostMapping("/products")
    public ResponseEntity<AdminDtos.ProductRow> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(provisioningService.createProduct(request.name(), request.manufacturer()));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID productId) {
        adminService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/provisioning/import", consumes = "multipart/form-data")
    public AdminDtos.ProvisioningResult importProvisioning(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "X-Admin-Actor", defaultValue = "local-admin") String actor) {
        return provisioningService.importCsv(file, actor);
    }

    @PutMapping("/tags/{uid}/metadata")
    public ResponseEntity<AdminDtos.TagRow> updateMetadata(@PathVariable String uid,
            @Valid @RequestBody MetadataRequest request) {
        return ResponseEntity.ok(adminService.updateMetadata(uid, request));
    }

    public record MetadataRequest(
            @Size(max = 255) String displayName,
            @Size(max = 2000) String description,
            @Size(max = 1000) String imageUrl) {
    }

    public record ProductRequest(
            @Size(min = 1, max = 255) String name,
            @Size(min = 1, max = 255) String manufacturer) {
    }

    public record BulkTagRequest(
            @NotEmpty List<@NotBlank String> uids,
            @NotBlank String action) {
    }
}
