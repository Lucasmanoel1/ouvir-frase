package com.lucasmanoel.idiomando;

import org.springframework.boot.SpringApplication;

public class TestIdiomandoApplication {

	public static void main(String[] args) {
		SpringApplication.from(IdiomandoApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
