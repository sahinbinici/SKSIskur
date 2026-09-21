package com.sks.sksiskur.sicil;

import com.sks.sksiskur.exception.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "sicil.enabled", havingValue = "true", matchIfMissing = true)
public class RemoteSicilUnitCatalog implements SicilUnitCatalog {

    private final JdbcTemplate isicil;
    private final JdbcTemplate asicil;

    public RemoteSicilUnitCatalog(
            @Qualifier("isicilJdbcTemplate") JdbcTemplate isicil,
            @Qualifier("asicilJdbcTemplate") JdbcTemplate asicil
    ) {
        this.isicil = isicil;
        this.asicil = asicil;
    }

    @Override
    public List<WorkUnit> workUnits() {
        try {
            return isicil.query("""
                            SELECT BRKODU, BRKK30, BRKO30, BRKDAC, KAMUKOD
                            FROM brkodu
                            WHERE BRKODU IS NOT NULL AND BRKODU <> 0
                              AND (ACKDUR IS NULL OR ACKDUR <> 1)
                              AND (
                                    (BRKK30 IS NOT NULL AND TRIM(BRKK30) <> '')
                                    OR (BRKO30 IS NOT NULL AND TRIM(BRKO30) <> '')
                                    OR (BRKDAC IS NOT NULL AND TRIM(BRKDAC) <> '')
                              )
                            """,
                    (rs, rowNum) -> {
                        String ad = firstNonBlank(rs.getString("BRKK30"), rs.getString("BRKO30"), rs.getString("BRKDAC"));
                        if (ad == null) {
                            ad = String.valueOf(rs.getInt("BRKODU"));
                        }
                        Integer kamu = rs.getObject("KAMUKOD") == null ? null : rs.getInt("KAMUKOD");
                        return new WorkUnit(String.valueOf(rs.getInt("BRKODU")), ad.trim(), rs.getString("BRKO30"), kamu, "ISICIL");
                    });
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "İdari sicil birim listesi alınamadı.");
        }
    }

    @Override
    public List<Faculty> faculties() {
        try {
            return asicil.query("""
                            SELECT FAKKOD, FAKACK, FAKA30, FAKK30, kisaad, KAMUKOD
                            FROM fakack
                            WHERE FAKKOD IS NOT NULL AND FAKKOD <> 0
                            """,
                    (rs, rowNum) -> {
                        List<String> aliases = new ArrayList<>();
                        addAlias(aliases, rs.getString("FAKACK"));
                        addAlias(aliases, rs.getString("FAKA30"));
                        addAlias(aliases, rs.getString("FAKK30"));
                        addAlias(aliases, rs.getString("kisaad"));
                        String ad = firstNonBlank(rs.getString("FAKA30"), rs.getString("FAKK30"), rs.getString("FAKACK"));
                        if (ad == null || ad.isBlank()) {
                            return null;
                        }
                        Integer kamu = rs.getObject("KAMUKOD") == null ? null : rs.getInt("KAMUKOD");
                        return new Faculty(rs.getInt("FAKKOD"), ad.trim(), kamu, aliases);
                    }).stream().filter(Objects::nonNull).toList();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Akademik sicil fakülte listesi alınamadı.");
        }
    }

    @Override
    public Optional<WorkUnit> authenticate(String birimKodu, String sifre) {
        if (birimKodu == null || sifre == null) {
            return Optional.empty();
        }
        try {
            int kod = Integer.parseInt(birimKodu.trim());
            List<AuthRow> rows = isicil.query("""
                            SELECT BRKODU, BRKK30, BRKO30, BRKDAC, KAMUKOD, sifres
                            FROM brkodu
                            WHERE BRKODU = ?
                              AND (ACKDUR IS NULL OR ACKDUR <> 1)
                            """,
                    (rs, rowNum) -> new AuthRow(
                            String.valueOf(rs.getInt("BRKODU")),
                            firstNonBlank(rs.getString("BRKK30"), rs.getString("BRKO30"), rs.getString("BRKDAC")),
                            rs.getString("BRKO30"),
                            rs.getObject("KAMUKOD") == null ? null : rs.getInt("KAMUKOD"),
                            rs.getString("sifres")
                    ),
                    kod);
            return rows.stream()
                    .filter(row -> passwordMatches(row.sifre(), sifre))
                    .map(row -> new WorkUnit(row.kod(), row.ad() == null ? row.kod() : row.ad().trim(), row.adUzun(), row.kamuKod(), "ISICIL"))
                    .findFirst();
        } catch (NumberFormatException ex) {
            return Optional.empty();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Birim girişi şu anda doğrulanamıyor.");
        }
    }

    private boolean passwordMatches(String stored, String given) {
        if (stored == null || stored.isBlank()) {
            return false;
        }
        byte[] left = stored.trim().getBytes(StandardCharsets.UTF_8);
        byte[] right = given.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private record AuthRow(String kod, String ad, String adUzun, Integer kamuKod, String sifre) {
    }

    private void addAlias(List<String> aliases, String value) {
        if (value != null && !value.isBlank() && !aliases.contains(value.trim())) {
            aliases.add(value.trim());
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
