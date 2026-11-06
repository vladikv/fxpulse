package com.vlad.fxpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan   // registers all @ConfigurationProperties records (e.g. NbpProperties)
public class FxPulseApplication {

	public static void main(String[] args) {
		SpringApplication.run(FxPulseApplication.class, args);
	}

}
