package com.tenpo.challenge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenpo.challenge.proxy.api.PercentageApi;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final EndpointsConfig.Endpoint percentageServiceEndpoint;
    private final ObjectMapper objectMapper;

    @Bean
    public PercentageApi percentageApi() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(percentageServiceEndpoint.getLoggingLevel());

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(percentageServiceEndpoint.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(percentageServiceEndpoint.getReadTimeout(), TimeUnit.MILLISECONDS)
                .addInterceptor(loggingInterceptor)
                .build();

        String baseUrl = percentageServiceEndpoint.getBaseUrl().endsWith("/")
                ? percentageServiceEndpoint.getBaseUrl()
                : percentageServiceEndpoint.getBaseUrl() + "/";

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .build();

        return retrofit.create(PercentageApi.class);
    }
}
