package com.anchor.api.web;

import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.persistence.entity.WatchAddressEntity;
import com.anchor.common.persistence.model.SwapRow;
import com.anchor.common.persistence.model.WatchAddressRow;
import com.anchor.api.service.swap.SwapQueryService;
import com.anchor.api.service.watch.WatchAddressService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
public class AnchorController {

    private final ChainRegistry chainRegistry;
    private final WatchAddressService watchAddressService;
    private final SwapQueryService swapQueryService;

    public AnchorController(ChainRegistry chainRegistry, WatchAddressService watchAddressService, SwapQueryService swapQueryService) {
        this.chainRegistry = chainRegistry;
        this.watchAddressService = watchAddressService;
        this.swapQueryService = swapQueryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reloadWatchSets() {
        watchAddressService.reloadAll();
    }

    @GetMapping("/api/v1/chains")
    public List<Map<String, Object>> chains() {
        return chainRegistry.all().stream().map(chain -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", chain.key());
            item.put("chainId", chain.chainId());
            item.put("name", chain.name());
            item.put("type", chain.type().name());
            item.put("testnet", chain.testnet());
            item.put("confirmationBlocks", chain.confirmationBlocks());
            return item;
        }).toList();
    }

    @GetMapping("/api/v1/watch-addresses")
    public List<Map<String, Object>> listWatch(@RequestParam @NotNull Long chainId) {
        return watchAddressService.list(chainId).stream().map(this::watchView).toList();
    }

    @PostMapping("/api/v1/watch-addresses")
    public ResponseEntity<Map<String, Object>> createWatch(@Valid @RequestBody WatchAddressRequest request) {
        WatchAddressEntity saved = watchAddressService.create(toEntity(request));
        WatchAddressRow row = watchAddressService.list(saved.getChainId()).stream()
                .filter(item -> item.getId().equals(saved.getId()))
                .findFirst()
                .orElseThrow(() -> new AnchorException("NOT_FOUND", "监控地址不存在"));
        return ResponseEntity.status(HttpStatus.CREATED).body(watchView(row));
    }

    @PutMapping("/api/v1/watch-addresses/{id}")
    public Map<String, Object> updateWatch(@PathVariable long id, @Valid @RequestBody WatchAddressRequest request) {
        WatchAddressEntity saved = watchAddressService.update(id, toEntity(request));
        return watchAddressService.list(saved.getChainId()).stream()
                .filter(item -> item.getId().equals(saved.getId()))
                .findFirst()
                .map(this::watchView)
                .orElseThrow(() -> new AnchorException("NOT_FOUND", "监控地址不存在"));
    }

    @DeleteMapping("/api/v1/watch-addresses/{id}")
    public ResponseEntity<Void> deleteWatch(@PathVariable long id) {
        watchAddressService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/swaps")
    public Map<String, Object> swaps(@RequestParam @NotNull Long chainId, @RequestParam(required = false) String address,
                                     @RequestParam(required = false) String dex, @RequestParam(required = false) String direction,
                                     @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        IPage<SwapRow> result = swapQueryService.list(chainId, address, dex, direction, page, size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getRecords().stream().map(this::swapView).toList());
        body.put("page", result.getCurrent());
        body.put("size", result.getSize());
        body.put("total", result.getTotal());
        return body;
    }

    private WatchAddressEntity toEntity(WatchAddressRequest request) {
        WatchAddressEntity entity = new WatchAddressEntity();
        entity.setChainId(request.chainId());
        entity.setAddress(request.address());
        entity.setName(request.name());
        entity.setTag(request.tag().name());
        entity.setNote(request.note());
        return entity;
    }

    private Map<String, Object> watchView(WatchAddressRow row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", String.valueOf(row.getId()));
        view.put("chainId", row.getChainId());
        view.put("address", row.getAddress());
        view.put("name", row.getName());
        view.put("tag", row.getTag());
        view.put("note", row.getNote());
        view.put("swapCount", row.getSwapCount());
        view.put("lastActiveAt", row.getLastActiveAt());
        view.put("createdAt", row.getCreatedAtMs());
        return view;
    }

    private Map<String, Object> swapView(SwapRow row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", String.valueOf(row.getId()));
        view.put("chainId", row.getChainId());
        view.put("txHash", row.getTxHash());
        view.put("logIndex", row.getLogIndex());
        view.put("blockNumber", row.getBlockNumber());
        view.put("timestamp", row.getTimestampMs());
        view.put("direction", row.getDirection());
        view.put("dex", row.getDex());
        view.put("tokenIn", token(row.getTokenIn(), row.getTokenInSymbol(), row.getTokenInDecimals(), row.getAmountIn()));
        view.put("tokenOut", token(row.getTokenOut(), row.getTokenOutSymbol(), row.getTokenOutDecimals(), row.getAmountOut()));
        view.put("confirmStatus", row.getConfirmStatus());
        view.put("watchedAddress", row.getWatchedAddress());
        return view;
    }

    private static Map<String, Object> token(String address, String symbol, Integer decimals, BigInteger amount) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("address", address);
        view.put("symbol", symbol);
        view.put("decimals", decimals);
        view.put("amount", amount == null ? null : amount.toString());
        return view;
    }

    public record WatchAddressRequest(
            @NotNull Long chainId,
            @NotBlank String address,
            String name,
            @NotNull com.anchor.common.domain.AddressTag tag,
            String note) {
    }
}
