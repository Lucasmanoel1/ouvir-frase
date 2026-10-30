package com.lucasmanoel.ouvirfrase.ingestao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("ingestao")
@EnableConfigurationProperties(IngestaoProperties.class)
public class IngestaoConfig {
}
