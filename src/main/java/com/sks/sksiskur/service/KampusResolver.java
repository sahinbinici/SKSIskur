package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.KampusTuru;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.sicil.FacultyUnitMatcher;
import com.sks.sksiskur.sicil.NameNormalizer;
import com.sks.sksiskur.sicil.SicilUnitCatalog;
import com.sks.sksiskur.sicil.WorkUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class KampusResolver {

    private final List<String> tasraKeywords;
    private final SicilUnitCatalog sicilUnitCatalog;
    private final FacultyUnitMatcher facultyUnitMatcher = new FacultyUnitMatcher();

    public KampusResolver(
            @Value("${app.kampus.tasra-keywords:Nizip,Oğuzeli,Araban,İslahiye,Nurdağı,Yavuzeli,Karkamış,Taşlıçay}") String tasraKeywords,
            SicilUnitCatalog sicilUnitCatalog
    ) {
        this.tasraKeywords = Arrays.stream(tasraKeywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(value -> NameNormalizer.fold(value))
                .toList();
        this.sicilUnitCatalog = sicilUnitCatalog;
    }

    public KampusTuru resolve(Student student) {
        String combined = NameNormalizer.fold(join(student.getFakulte(), student.getBolum(), student.getProgram()));
        for (String keyword : tasraKeywords) {
            if (combined.contains(keyword)) {
                return KampusTuru.TASRA;
            }
        }
        return KampusTuru.MERKEZ;
    }

    public String resolveTasraBirimAdi(Student student) {
        return facultyUnitMatcher.preferredUnit(
                        student.getFakulte(),
                        student.getBolum(),
                        sicilUnitCatalog.faculties(),
                        sicilUnitCatalog.workUnits()
                )
                .map(WorkUnit::displayName)
                .filter(name -> !name.isBlank())
                .orElse(firstNonBlank(student.getFakulte(), student.getBolum(), student.getProgram(), "biriminiz"));
    }

    public String buildImzaMesaji(Student student) {
        return buildImzaMesaji(student, null);
    }

    /**
     * Verilen şablonu kullanarak öğrenciye ait imza mesajını oluşturur.
     *
     * <p>Desteklenen yer tutucular: {@code {AD_SOYAD}}, {@code {BIRIM}}.
     * {@code emailSablon} boş veya {@code null} ise sistem varsayılan metni döner.
     */
    public String buildImzaMesaji(Student student, String emailSablon) {
        String birimAdi = resolve(student) == KampusTuru.MERKEZ
                ? "SKS Daire Başkanlığı"
                : resolveTasraBirimAdi(student);

        if (emailSablon != null && !emailSablon.isBlank()) {
            return emailSablon
                    .replace("{AD_SOYAD}", student.getAdSoyad())
                    .replace("{BIRIM}", birimAdi);
        }

        // Varsayılan mesaj
        if (resolve(student) == KampusTuru.MERKEZ) {
            return "Sayın " + student.getAdSoyad()
                    + ", İŞKUR nihai listesine alındınız. Birim ataması için SKS Daire Başkanlığı'na gelerek sözleşme imzalamanız gerekmektedir.";
        }
        return "Sayın " + student.getAdSoyad()
                + ", İŞKUR nihai listesine alındınız. Birim ataması için bulunduğunuz "
                + birimAdi + " birimine gelerek sözleşme imzalamanız gerekmektedir.";
    }

    public String buildEmailSubject() {
        return "İŞKUR Nihai Liste – Sözleşme İmza Daveti";
    }

    private String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(value.trim());
            }
        }
        return builder.toString().toLowerCase(Locale.forLanguageTag("tr-TR"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
