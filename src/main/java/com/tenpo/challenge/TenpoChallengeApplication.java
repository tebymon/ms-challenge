package com.tenpo.challenge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TenpoChallengeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenpoChallengeApplication.class, args);
    }
}
