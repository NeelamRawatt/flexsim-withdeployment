package com.example.simulation_service;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SimulationServiceApplication {

	public static void main(String[] args) {
				        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		SpringApplication.run(SimulationServiceApplication.class, args);
	}

}
