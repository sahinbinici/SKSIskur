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

/** Removes the former one-document-per-type constraint so household SGK records can be uploaded separately. */
@Component
@Order(6)
public class BasvuruBelgesiSchemaMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BasvuruBelgesiSchemaMigrator.class);
    private final DataSource dataSource;

    public BasvuruBelgesiSchemaMigrator(@Qualifier("dataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                for (Map.Entry<String, List<String>> index : uniqueIndexes(connection).entrySet()) {
                    if (index.getValue().equals(List.of("basvuru_id", "belge_tipi"))) {
                        ensureForeignKeyIndex(connection, statement);
                        String name = index.getKey().replace("`", "``");
                        statement.execute("ALTER TABLE basvuru_belgeleri DROP INDEX `" + name + "`");
                        log.info("Tekil başvuru belgesi indeksi kaldırıldı: {}", index.getKey());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Başvuru belgesi şema geçişi atlandı: {}", ex.getMessage());
        }
    }

    private void ensureForeignKeyIndex(Connection connection, Statement statement) throws Exception {
        try (ResultSet result = connection.getMetaData().getIndexInfo(connection.getCatalog(), null,
                "basvuru_belgeleri", false, false)) {
            while (result.next()) {
                if ("idx_basvuru_belgeleri_basvuru_id".equalsIgnoreCase(result.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        statement.execute("CREATE INDEX idx_basvuru_belgeleri_basvuru_id ON basvuru_belgeleri (basvuru_id)");
    }

    private Map<String, List<String>> uniqueIndexes(Connection connection) throws Exception {
        Map<String, List<String>> indexes = new LinkedHashMap<>();
        try (ResultSet result = connection.getMetaData().getIndexInfo(connection.getCatalog(), null,
                "basvuru_belgeleri", true, false)) {
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
