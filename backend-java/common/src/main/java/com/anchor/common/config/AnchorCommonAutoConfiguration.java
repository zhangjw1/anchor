package com.anchor.common.config;

import com.anchor.common.chain.AnchorSettings;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.chain.YamlChainRegistry;
import com.anchor.common.handler.BigIntegerNumericTypeHandler;
import com.anchor.common.manager.FailoverRpcGateway;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.math.BigInteger;

/**
 * 装配链注册表、RPC 网关、分页插件和金额类型处理器。
 */
@AutoConfiguration
@EnableConfigurationProperties(AnchorSettings.class)
@MapperScan("com.anchor.common.dao")
public class AnchorCommonAutoConfiguration {
    /**
     * 按配置文件创建链注册表。
     */
    @Bean
    public ChainRegistry chainRegistry(AnchorSettings settings) {
        return new YamlChainRegistry(settings);
    }
    /**
     * 创建带故障切换和指标的 RPC 网关。
     */
    @Bean
    public RpcGateway rpcGateway(ChainRegistry chainRegistry, ObjectProvider<MeterRegistry> meters) {
        MeterRegistry meterRegistry = meters.getIfAvailable(SimpleMeterRegistry::new);
        return new FailoverRpcGateway(chainRegistry, meterRegistry);
    }
    /**
     * PostgreSQL 分页插件。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
    /**
     * 把 BigInteger 按 NUMERIC 读写，避免金额被截断。
     */
    @Bean
    public ConfigurationCustomizer anchorTypeHandlers() {
        return configuration -> configuration.getTypeHandlerRegistry()
                .register(BigInteger.class, JdbcType.NUMERIC, new BigIntegerNumericTypeHandler());
    }
}
