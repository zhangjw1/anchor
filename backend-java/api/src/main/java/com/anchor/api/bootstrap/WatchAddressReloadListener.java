package com.anchor.api.bootstrap;

import com.anchor.api.service.WatchAddressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WatchAddressReloadListener {

    private static final Logger log = LoggerFactory.getLogger(WatchAddressReloadListener.class);

    private final WatchAddressService watchAddressService;

    public WatchAddressReloadListener(WatchAddressService watchAddressService) {
        this.watchAddressService = watchAddressService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reloadWatchSets() {
        watchAddressService.reloadAll();
        log.info("watch addresses reloaded");
    }
}
