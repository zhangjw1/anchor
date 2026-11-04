package com.anchor.indexer.controller;

import com.anchor.common.chain.ChainScanner;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 扫描器运维接口：暂停后可手动恢复。
 */
@RestController
public class ScannerAdminController {

    private final ChainScanner chainScanner;

    public ScannerAdminController(ChainScanner chainScanner) {
        this.chainScanner = chainScanner;
    }

    @PostMapping("/admin/scanner/{chainId}/resume")
    public ResponseEntity<Map<String, Object>> resume(@PathVariable long chainId) {
        chainScanner.resume(chainId);
        return ResponseEntity.ok(Map.of("chainId", chainId, "paused", chainScanner.isPaused()));
    }

    @GetMapping("/admin/scanner/{chainId}/status")
    public Map<String, Object> status(@PathVariable long chainId) {
        return Map.of("chainId", chainId, "paused", chainScanner.isPaused());
    }
}
