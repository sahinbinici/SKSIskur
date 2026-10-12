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

@Component
@Order(7)
public class TakipDonemSchemaMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TakipDonemSchemaMigrator.class);
    private final DataSource dataSource;

    public TakipDonemSchemaMigrator(@Qualifier("dataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
            addColumn(connection, statement, "ekuant_status", "VARCHAR(20) NOT NULL DEFAULT 'DRAFT'");
            addColumn(connection, statement, "puantaj_status", "VARCHAR(20) NOT NULL DEFAULT 'DRAFT'");
            addColumn(connection, statement, "ekuant_gonderim_tarihi", "DATETIME(6) NULL");
            addColumn(connection, statement, "puantaj_gonderim_tarihi", "DATETIME(6) NULL");
            addColumn(connection, statement, "ekuant_onay_tarihi", "DATETIME(6) NULL");
            addColumn(connection, statement, "puantaj_onay_tarihi", "DATETIME(6) NULL");
            statement.executeUpdate("""
                    UPDATE takip_donemleri
                    SET ekuant_status = status,
                        puantaj_status = status,
                        ekuant_gonderim_tarihi = CASE WHEN status IN ('SUBMITTED', 'APPROVED') THEN gonderim_tarihi ELSE ekuant_gonderim_tarihi END,
                        puantaj_gonderim_tarihi = CASE WHEN status IN ('SUBMITTED', 'APPROVED') THEN gonderim_tarihi ELSE puantaj_gonderim_tarihi END,
                        ekuant_onay_tarihi = CASE WHEN status = 'APPROVED' THEN onay_tarihi ELSE ekuant_onay_tarihi END,
                        puantaj_onay_tarihi = CASE WHEN status = 'APPROVED' THEN onay_tarihi ELSE puantaj_onay_tarihi END
                    WHERE ekuant_status = 'DRAFT' AND puantaj_status = 'DRAFT' AND status <> 'DRAFT'
                    """);
        } catch (Exception ex) {
            log.warn("Takip dönem şema geçişi atlandı: {}", ex.getMessage());
        }
    }

    private void addColumn(Connection connection, Statement statement, String column, String definition) throws Exception {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "takip_donemleri", column)) {
            if (columns.next()) {
                return;
            }
        }
        statement.execute("ALTER TABLE takip_donemleri ADD COLUMN `" + column + "` " + definition);
        log.info("takip_donemleri sütunu eklendi: {}", column);
    }
}
