package com.herafi.auth_service.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class UserServiceClientConfig {
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

    @Bean
    RestClient userServiceRestClient(
            @LoadBalanced RestClient.Builder builder,
            RequestIdInterceptor requestIdInterceptor) {
        return builder.baseUrl("http://USER-SERVICE")
                .requestInterceptor(requestIdInterceptor)
                .build();
    }

}
