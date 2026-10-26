package com.anchor.indexer.bootstrap;

import com.anchor.common.chain.AnchorSettings;
import com.anchor.common.chain.ChainScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ScannerLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ScannerLifecycle.class);

    private final AnchorSettings settings;
    private final ChainScanner chainScanner;

    public ScannerLifecycle(AnchorSettings settings, ChainScanner chainScanner) {
        this.settings = settings;
        this.chainScanner = chainScanner;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!settings.getScanner().isEnabled()) {
            log.info("scanner disabled");
            return;
        }
        log.info("scanner starting chainKey={}", settings.getChainKey());
        Thread.ofVirtual().name("block-scanner").start(() -> {
            try {
                chainScanner.scanForever();
            } catch (RuntimeException ex) {
                log.error("scanner stopped", ex);
            }
        });
    }
}
