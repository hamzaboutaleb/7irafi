package com.herafi.user_service.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Two builders: a plain one for anything that needs a literal address, and a
 * load-balanced one whose host part is a service name resolved through Eureka.
 */
@Configuration
public class ServiceClientConfig {

    @Primary
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @LoadBalanced
    @Bean
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
