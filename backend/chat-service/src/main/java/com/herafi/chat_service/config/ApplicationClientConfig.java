package com.herafi.chat_service.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ApplicationClientConfig {
    @LoadBalanced
    @Bean
    RestClient.Builder loadBalancedRestClientBuilder(RequestIdInterceptor requestIdInterceptor) {
        return RestClient.builder().requestInterceptor(requestIdInterceptor);
    }

    @Bean
    RestClient applicationServiceRestClient(@LoadBalanced RestClient.Builder builder) {
        // Explicit timeouts: the default is to wait forever, which turns one slow
        // service into a frozen one here. Connect = time to reach it at all,
        // read = time to answer once reached.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));

        return builder.baseUrl("http://APPLICATION-SERVICE")
                .requestFactory(factory)
                .build();
    }

}
