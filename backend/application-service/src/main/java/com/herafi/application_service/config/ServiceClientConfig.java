package com.herafi.application_service.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Two builders: a plain one, and a load-balanced one whose host part is a
 * service name resolved through Eureka.
 */
@Configuration
public class ServiceClientConfig {

    @Primary
    @Bean
    RestClient.Builder restClientBuilder(RequestIdInterceptor requestIdInterceptor) {
        return RestClient.builder().requestInterceptor(requestIdInterceptor);
    }

    @LoadBalanced
    @Bean
    RestClient.Builder loadBalancedRestClientBuilder(RequestIdInterceptor requestIdInterceptor) {
        return RestClient.builder().requestInterceptor(requestIdInterceptor);
    }
}
