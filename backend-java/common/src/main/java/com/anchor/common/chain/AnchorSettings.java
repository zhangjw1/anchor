package com.anchor.common.chain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * anchor.* 配置，包括链文件、默认链和扫描开关。
 */
@ConfigurationProperties(prefix = "anchor")
public class AnchorSettings {

    private String chainsFile;
    private String chainKey = "base";
    private Scanner scanner = new Scanner();
    private Reorg reorg = new Reorg();
    private Http http = new Http();

    public String getChainsFile() {
        return chainsFile;
    }

    public void setChainsFile(String chainsFile) {
        this.chainsFile = chainsFile;
    }

    public String getChainKey() {
        return chainKey;
    }

    public void setChainKey(String chainKey) {
        this.chainKey = chainKey;
    }

    public Scanner getScanner() {
        return scanner;
    }

    public void setScanner(Scanner scanner) {
        this.scanner = scanner;
    }

    public Reorg getReorg() {
        return reorg;
    }

    public void setReorg(Reorg reorg) {
        this.reorg = reorg;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }

    /**
     * 扫描器开关和轮询间隔。
     */
    public static class Scanner {
        private boolean enabled;
        // 轮询间隔，单位毫秒
        private long pollIntervalMs = 2000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }
    }

    /**
     * 重组相关：本地区块哈希保留窗口。
     */
    public static class Reorg {
        /** 至少保留的最近区块哈希数量；且不会删掉 finalized 及之后的哈希。 */
        private int hashWindow = 512;

        public int getHashWindow() {
            return hashWindow;
        }

        public void setHashWindow(int hashWindow) {
            this.hashWindow = hashWindow;
        }
    }

    /**
     * HTTP 入口相关。默认不信任客户端自带的 X-Forwarded-For，防止伪造限流键。
     */
    public static class Http {
        /** 仅在前面有可信反向代理时设为 true。 */
        private boolean trustForwardedHeaders = false;

        public boolean isTrustForwardedHeaders() {
            return trustForwardedHeaders;
        }

        public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
            this.trustForwardedHeaders = trustForwardedHeaders;
        }
    }
}
