package com.anchor.api.support;

import com.anchor.common.chain.AnchorSettings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 解析客户端 IP：默认只用 remoteAddr；仅当配置信任反代时才读 X-Forwarded-For。
 */
@Component
public class ClientIpResolver {

    private final AnchorSettings settings;

    public ClientIpResolver(AnchorSettings settings) {
        this.settings = settings;
    }

    public String resolve(HttpServletRequest request) {
        if (settings.getHttp().isTrustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
