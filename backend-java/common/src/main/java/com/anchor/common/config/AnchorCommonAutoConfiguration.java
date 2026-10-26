package com.anchor.common.config;

import com.anchor.common.chain.AnchorSettings;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.chain.YamlChainRegistry;
import com.anchor.common.persistence.typehandler.BigIntegerNumericTypeHandler;
import com.anchor.common.rpc.FailoverRpcGateway;
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

@AutoConfiguration
@EnableConfigurationProperties(AnchorSettings.class)
@MapperScan("com.anchor.common.persistence.mapper")
public class AnchorCommonAutoConfiguration {

    @Bean
    public ChainRegistry chainRegistry(AnchorSettings settings) {
        return new YamlChainRegistry(settings);
    }

    @Bean
    public RpcGateway rpcGateway(ChainRegistry chainRegistry, ObjectProvider<MeterRegistry> meters) {
        MeterRegistry meterRegistry = meters.getIfAvailable(SimpleMeterRegistry::new);
        return new FailoverRpcGateway(chainRegistry, meterRegistry);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    @Bean
    public ConfigurationCustomizer anchorTypeHandlers() {
        return configuration -> configuration.getTypeHandlerRegistry()
                .register(BigInteger.class, JdbcType.NUMERIC, new BigIntegerNumericTypeHandler());
    }
}
