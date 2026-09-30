package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.BelgeDogrulamaDurumu;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.exception.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BelgeOcrService {
    private final String executable;
    private final String tessdata;

    public BelgeOcrService(@Value("${app.ocr.tesseract-path:C:/Program Files/Tesseract-OCR/tesseract.exe}") String executable,
                           @Value("${app.ocr.tessdata-dir:C:/Users/cdikici/AppData/Local/SKSIskur/tessdata}") String tessdata) {
        this.executable = executable;
        this.tessdata = tessdata;
    }

    public BelgeDogrulamaDurumu verify(Path source, String contentType, Student student, DocumentType type) {
        return review(source, contentType, student, type).durum();
    }

    public BelgeOcrReviewResult review(Path source, String contentType, Student student, DocumentType type) {
        try {
            String text = normalize(extract(source, contentType));
            if (text.isBlank()) {
                return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                        "Belge metni okunamadı. Net PDF/görsel ve e-Devlet barkodlu çıktı olup olmadığını kontrol edin.");
            }
            if (!hasExpectedTitle(text, type)) {
                return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                        type.getLabel() + " başlığı bulunamadı. Doğru belge türü yüklendi mi kontrol edin.");
            }
            if (containsAllWords(text, student.getAd()) && containsAllWords(text, student.getSoyad())) {
                return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.DOGRULANDI,
                        "Belge başlığı ve öğrenci ad-soyadı OCR ile eşleşti.");
            }
            return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                    "Ad-soyad OCR ile tam eşleşmedi. Belgeyi açıp öğrenci bilgisiyle karşılaştırın.");
        } catch (Exception ex) {
            return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                    "OCR çalıştırılamadı; belgeyi manuel inceleyin.");
        }
    }

    private boolean hasExpectedTitle(String text, DocumentType type) {
        return switch (type) {
            case KIMLIK_BELGESI -> text.contains("KIMLIK") &&
                    (text.contains("TURKIYE CUMHURIYETI") || text.contains("T C"));
            case OGRENCI_BELGESI -> text.contains("OGRENCI BELGESI");
            case ADLI_SICIL -> text.contains("ADLI SICIL") || text.contains("ADLI SICIL KAYDI");
            case IKAMETGAH -> text.contains("YERLESIM YERI") || text.contains("IKAMETGAH");
            default -> false;
        };
    }

    private boolean containsAllWords(String text, String value) {
        for (String word : normalize(value).split(" ")) {
            if (!word.isBlank() && !text.matches(".*\\b" + java.util.regex.Pattern.quote(word) + "\\b.*")) return false;
        }
        return true;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").trim();
    }

    private String extract(Path source, String contentType) {
        List<Path> images = new ArrayList<>();
        try {
            if (contentType != null && contentType.contains("pdf")) {
                try (PDDocument document = Loader.loadPDF(source.toFile())) {
                    PDFRenderer renderer = new PDFRenderer(document);
                    for (int i = 0; i < Math.min(document.getNumberOfPages(), 2); i++) {
                        BufferedImage image = renderer.renderImageWithDPI(i, 300);
                        Path png = Files.createTempFile("belge-ocr-", ".png");
                        ImageIO.write(image, "png", png.toFile());
                        images.add(png);
                    }
                }
            } else images.add(source);
            StringBuilder result = new StringBuilder();
            for (Path image : images) result.append(runTesseract(image)).append('\n');
            return result.toString();
        } catch (Exception ex) {
            return "";
        } finally {
            images.stream().filter(path -> !path.equals(source)).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }

    private String runTesseract(Path image) throws Exception {
        Process process = new ProcessBuilder(executable, image.toString(), "stdout", "-l", "tur+eng",
                "--tessdata-dir", tessdata).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException(output);
        return output;
    }
}
