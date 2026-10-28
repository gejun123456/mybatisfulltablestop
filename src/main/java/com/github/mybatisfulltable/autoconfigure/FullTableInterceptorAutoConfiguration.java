package com.github.mybatisfulltable.autoconfigure;

import com.github.mybatisfulltable.config.FullTableProperties;
import com.github.mybatisfulltable.interceptor.FullTableInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for MyBatis Full Table Interceptor (JSqlParser version).
 */
@Configuration
@ConditionalOnClass(Interceptor.class)
@EnableConfigurationProperties(FullTableProperties.class)
public class FullTableInterceptorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FullTableInterceptor.class)
    public FullTableInterceptor fullTableInterceptor(FullTableProperties properties) {
        return new FullTableInterceptor(properties);
    }
}
