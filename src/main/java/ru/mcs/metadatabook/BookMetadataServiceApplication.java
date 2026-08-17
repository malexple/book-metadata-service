package ru.mcs.metadatabook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.mcs.metadatabook.config.BookProperties;

@SpringBootApplication
@EnableConfigurationProperties(BookProperties.class)
public class BookMetadataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookMetadataServiceApplication.class, args);
    }
}