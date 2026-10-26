package com.anchor.api.controller;

import com.anchor.api.service.WatchAddressService;
import com.anchor.api.vo.WatchAddressRequest;
import com.anchor.api.vo.WatchAddressVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 监控地址的增删改查。
 */
@Validated
@RestController
public class WatchAddressController {

    private final WatchAddressService watchAddressService;

    public WatchAddressController(WatchAddressService watchAddressService) {
        this.watchAddressService = watchAddressService;
    }
    /**
     * 按链列出监控地址，附带交换次数和最近活跃时间。
     */
    @GetMapping("/api/v1/watch-addresses")
    public List<WatchAddressVO> list(@RequestParam @NotNull Long chainId) {
        return watchAddressService.list(chainId).stream().map(WatchAddressVO::from).toList();
    }
    /**
     * 新增监控地址。EVM 地址会先规范成小写。
     */
    @PostMapping("/api/v1/watch-addresses")
    public ResponseEntity<WatchAddressVO> create(@Valid @RequestBody WatchAddressRequest request) {
        WatchAddressVO saved = WatchAddressVO.from(watchAddressService.create(request.chainId(), request.address(), request.name(), request.tag(), request.note()));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
    /**
     * 按主键更新监控地址，并同步 Redis 中的旧地址。
     */
    @PutMapping("/api/v1/watch-addresses/{id}")
    public WatchAddressVO update(@PathVariable long id, @Valid @RequestBody WatchAddressRequest request) {
        return WatchAddressVO.from(watchAddressService.update(id, request.chainId(), request.address(), request.name(), request.tag(), request.note()));
    }
    /**
     * 物理删除监控地址，并从 Redis 集合移除。
     */
    @DeleteMapping("/api/v1/watch-addresses/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        watchAddressService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
