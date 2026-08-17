package ru.mcs.metadatabook.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pivot")
public class BookProperties {

    private Generator generator = new Generator();
    private Benchmark benchmark = new Benchmark();

    @Data
    public static class Generator {
        private int batchSize = 1000;
        private int defaultOrgId = 1;
    }

    @Data
    public static class Benchmark {
        private int warmupIterations = 3;
        private int testIterations = 10;
    }
}