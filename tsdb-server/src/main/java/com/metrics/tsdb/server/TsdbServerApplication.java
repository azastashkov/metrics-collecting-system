package com.metrics.tsdb.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.metrics.tsdb.server")
public class TsdbServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TsdbServerApplication.class, args);
    }
}
