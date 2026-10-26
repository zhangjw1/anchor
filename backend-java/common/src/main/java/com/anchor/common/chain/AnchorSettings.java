package com.anchor.common.chain;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anchor")
public class AnchorSettings {

    private String chainsFile;
    private String chainKey = "base";
    private Scanner scanner = new Scanner();

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

    public static class Scanner {
        private boolean enabled;
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
}
