package com.polakowski.requestmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of the request management service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RequestManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(RequestManagementApplication.class, args);
    }
}
