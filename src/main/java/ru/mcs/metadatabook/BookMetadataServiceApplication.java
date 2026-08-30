package ru.mcs.metadatabook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.mcs.metadatabook.config.BookProperties;
import ru.mcs.metadatabook.config.GoogleBooksProperties;
import ru.mcs.metadatabook.config.LibGenProperties;
import ru.mcs.metadatabook.config.SiglaProperties;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({BookProperties.class, GoogleBooksProperties.class, LibGenProperties.class, SiglaProperties.class})
public class BookMetadataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookMetadataServiceApplication.class, args);
    }
}