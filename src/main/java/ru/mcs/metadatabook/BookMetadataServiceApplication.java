package ru.mcs.metadatabook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.mcs.metadatabook.config.BookProperties;
import ru.mcs.metadatabook.config.GoogleBooksProperties;
import ru.mcs.metadatabook.config.LibGenProperties;
import ru.mcs.metadatabook.config.LitresProperties;

@SpringBootApplication
@EnableConfigurationProperties({BookProperties.class, LitresProperties.class, GoogleBooksProperties.class, LibGenProperties.class})
public class BookMetadataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookMetadataServiceApplication.class, args);
    }
}