package com.sks.sksiskur.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(5)
public class BasvuruDonemiSchemaMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BasvuruDonemiSchemaMigrator.class);
    private final DataSource dataSource;

    public BasvuruDonemiSchemaMigrator(@Qualifier("dataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
            Map<String, List<String>> indexes = uniqueIndexes(connection);
            try (Statement statement = connection.createStatement()) {
                for (Map.Entry<String, List<String>> entry : indexes.entrySet()) {
                    if (entry.getValue().equals(List.of("ogrenci_id"))) {
                        statement.execute("ALTER TABLE basvurular DROP INDEX `" + entry.getKey().replace("`", "``") + "`");
                        log.info("Eski tekil öğrenci başvuru indeksi kaldırıldı: {}", entry.getKey());
                    }
                }
                if (!indexes.containsValue(List.of("ogrenci_id", "basvuru_donemi_id"))) {
                    statement.execute("ALTER TABLE basvurular ADD CONSTRAINT uk_basvuru_ogrenci_donem UNIQUE (ogrenci_id, basvuru_donemi_id)");
                }
            }
        } catch (Exception ex) {
            log.warn("Başvuru dönemi şema geçişi atlandı: {}", ex.getMessage());
        }
    }

    private Map<String, List<String>> uniqueIndexes(Connection connection) throws Exception {
        Map<String, List<String>> indexes = new LinkedHashMap<>();
        try (ResultSet result = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "basvurular", true, false)) {
            while (result.next()) {
                String name = result.getString("INDEX_NAME");
                String column = result.getString("COLUMN_NAME");
                if (name != null && column != null && !"PRIMARY".equalsIgnoreCase(name)) {
                    indexes.computeIfAbsent(name, ignored -> new ArrayList<>()).add(column.toLowerCase());
                }
            }
        }
        return indexes;
    }
}
