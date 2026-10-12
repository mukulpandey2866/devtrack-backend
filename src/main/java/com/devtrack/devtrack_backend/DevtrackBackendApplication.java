package com.devtrack.devtrack_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class DevtrackBackendApplication {

	public static void main(String[] args) {
		String zone = System.getenv().getOrDefault("APP_TIME_ZONE", "UTC");
		TimeZone.setDefault(TimeZone.getTimeZone(zone));
		SpringApplication.run(DevtrackBackendApplication.class, args);
	}

}
