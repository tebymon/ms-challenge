package com.tenpo.challenge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tenpo Challenge API")
                        .version("1.0.0")
                        .description("""
                                API REST para el challenge técnico de Tenpo (rol Technical Lead).
                                
                                **Funcionalidades:**
                                - Cálculo con porcentaje dinámico (servicio externo + caché 30 min)
                                - Rate Limiting: 3 RPM
                                - Historial paginado de llamadas (registro asíncrono)
                                - Reintentos automáticos (máx 3) con Resilience4j
                                """)
                        .contact(new Contact()
                                .name("Tenpo Engineering")
                                .url("https://tenpo.cl")
                        )
                )
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")
                ));
    }
}
