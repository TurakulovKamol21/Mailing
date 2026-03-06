package com.company.mailing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan("com.company.mailing")
@EnableFeignClients(basePackages = "com.company.mailing.feign")
public class MailingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailingApplication.class, args);
    }
}
