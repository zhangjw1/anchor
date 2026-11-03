package com.anchor.api.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 查询服务入口：监控地址、交换记录、资产、报价和 WebSocket 推送。
 */
@SpringBootApplication(scanBasePackages = "com.anchor.api")
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
