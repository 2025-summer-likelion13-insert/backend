package com.example.insert.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PerformIngestProperties.class)
public class PerformConfig { }

