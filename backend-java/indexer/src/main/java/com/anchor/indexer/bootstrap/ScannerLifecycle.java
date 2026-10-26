package com.anchor.indexer.bootstrap;

import com.anchor.common.chain.AnchorSettings;
import com.anchor.common.chain.ChainScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 在独立线程里启动扫描，异常只记日志，不把进程打退。
 */
@Component
public class ScannerLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ScannerLifecycle.class);

    private final AnchorSettings settings;
    private final ChainScanner chainScanner;

    public ScannerLifecycle(AnchorSettings settings, ChainScanner chainScanner) {
        this.settings = settings;
        this.chainScanner = chainScanner;
    }
    /**
     * 配置关闭扫描时直接返回。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!settings.getScanner().isEnabled()) {
            log.info("扫描器未启用");
            return;
        }
        log.info("扫描器启动，链={}", settings.getChainKey());
        Thread.ofVirtual().name("block-scanner").start(() -> {
            try {
                chainScanner.scanForever();
            } catch (RuntimeException ex) {
                log.error("扫描器已停止", ex);
            }
        });
    }
}
