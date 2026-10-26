package com.anchor.parser.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 解析模块的进程入口。解析本身在索引进程里执行，这里只提供健康检查。
 */
@SpringBootApplication(scanBasePackages = "com.anchor.parser")
public class ParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParserApplication.class, args);
    }
}
