package com.herafi.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

@Configuration
public class UserServiceClientConfig {
    @Bean
    RestClient userServiceRestClient(
        @Value("${services.user-service.url}") String userServiceUrl
    )
    {
        return RestClient.builder()
                        .baseUrl(userServiceUrl)
                        .build();
    }

}
