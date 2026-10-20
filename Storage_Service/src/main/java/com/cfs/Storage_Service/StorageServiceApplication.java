package com.cfs.Storage_Service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.cfs.Storage_Service.Config.StorageProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)

public class StorageServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(StorageServiceApplication.class, args);
	}

}
