package ru.mcs.metadatabook.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Book Metadata Service API")
                        .description("Публичный сервис метаданных книг: поиск по ISBN/названию с выбором полей " +
                                "через ?fields=, полностью автоматическое обогащение из открытых источников " +
                                "(Open Library, Google Books, FantLab, опционально Sigla/LibGen) при первом " +
                                "обращении к книге, которой ещё нет в базе. Без регистрации и без apiKey. " +
                                "Книги, не найденные ни в одном источнике, уходят в очередь unresolved_lookup.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Malexple")
                                .email("malexple@gmail.ru"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local Dev")
                ));
    }
}