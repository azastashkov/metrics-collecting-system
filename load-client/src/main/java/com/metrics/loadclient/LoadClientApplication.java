package com.metrics.loadclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.metrics.loadclient.config.LoadClientProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(LoadClientProperties.class)
public class LoadClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadClientApplication.class, args);
    }
}
