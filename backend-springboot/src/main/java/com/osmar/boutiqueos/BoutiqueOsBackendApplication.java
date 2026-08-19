package com.osmar.boutiqueos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BoutiqueOsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BoutiqueOsBackendApplication.class, args);
	}

}
