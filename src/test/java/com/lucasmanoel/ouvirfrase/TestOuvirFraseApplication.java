package com.lucasmanoel.ouvirfrase;

import org.springframework.boot.SpringApplication;

public class TestOuvirFraseApplication {

	public static void main(String[] args) {
		SpringApplication.from(OuvirFraseApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
