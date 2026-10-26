package com.anchor.api.config;

import com.anchor.api.controller.WebSocketPushHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册 /ws，并打开心跳和指标的定时任务。
 */
@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketPushHandler webSocketPushHandler;

    public WebSocketConfig(WebSocketPushHandler webSocketPushHandler) {
        this.webSocketPushHandler = webSocketPushHandler;
    }
    /**
     * 把推送处理器挂到 /ws，允许浏览器跨源连接。
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketPushHandler, "/ws").setAllowedOrigins("*");
    }
}
