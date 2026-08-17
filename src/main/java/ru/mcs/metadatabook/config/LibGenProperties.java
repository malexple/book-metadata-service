package ru.mcs.metadatabook.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "libgen")
public class LibGenProperties {

    /**
     * По умолчанию выключен — осознанное решение, т.к. LibGen работает в
     * юридически серой зоне (теневая библиотека). Включать явным образом.
     */
    private boolean enabled = false;

    private String baseUrl = "https://libgen.li/json.php";
}