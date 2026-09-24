package com.example.productauth.api;

import com.example.productauth.api.dto.AdminDtos;
import com.example.productauth.service.AdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "${app.frontend-origin:http://localhost:5173}")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public AdminDtos.Overview overview() {
        return adminService.overview();
    }

    @GetMapping("/tags")
    public Page<AdminDtos.TagRow> tags(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "") String uid) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return adminService.tags(PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "lastScanCounter")), uid);
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
}
