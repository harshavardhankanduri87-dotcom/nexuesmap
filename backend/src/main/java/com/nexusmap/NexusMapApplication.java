package com.nexusmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NexusMapApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusMapApplication.class, args);
    }
}
