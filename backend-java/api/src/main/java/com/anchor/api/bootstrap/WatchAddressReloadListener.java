package com.anchor.api.bootstrap;

import com.anchor.api.service.WatchAddressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 进程就绪后把各链监控地址重新装入 Redis，避免重启后扫描漏地址。
 */
@Component
public class WatchAddressReloadListener {

    private static final Logger log = LoggerFactory.getLogger(WatchAddressReloadListener.class);

    private final WatchAddressService watchAddressService;

    public WatchAddressReloadListener(WatchAddressService watchAddressService) {
        this.watchAddressService = watchAddressService;
    }
    /**
     * 启动完成后刷新全部链的监控地址集合。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reloadWatchSets() {
        watchAddressService.reloadAll();
        log.info("监控地址已重新加载");
    }
}
