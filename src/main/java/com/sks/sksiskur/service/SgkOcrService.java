package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.BelgeDogrulamaDurumu;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SgkOcrService {
    private static final Pattern DATE = Pattern.compile("(\\d{2})[./-](\\d{2})[./-](\\d{4})");
    private static final Pattern START = Pattern.compile("(?is)(işe\\s*giriş|isyeri\\s*giriş|işyeri\\s*giris).{0,80}?(\\d{2}[./-]\\d{2}[./-]\\d{4})");
    private static final Pattern INCOME = Pattern.compile("(?is)(prime\\s+esas\\s+kazanç|brüt\\s+kazanç|kazanç\\s+toplamı).{0,60}?([0-9.]{1,15},[0-9]{2})");
    private final String executable;
    private final String tessdata;

    public SgkOcrService(@Value("${app.ocr.tesseract-path:C:/Program Files/Tesseract-OCR/tesseract.exe}") String executable,
                         @Value("${app.ocr.tessdata-dir:C:/Users/cdikici/AppData/Local/SKSIskur/tessdata}") String tessdata) {
        this.executable = executable;
        this.tessdata = tessdata;
    }

    public BelgeDogrulamaDurumu verify(Path source, String contentType, BigDecimal limit, Student student) {
        return review(source, contentType, limit, student).durum();
    }

    public BelgeOcrReviewResult review(Path source, String contentType, BigDecimal limit, Student student) {
        try {
            String rawText = extract(source, contentType);
            String text = rawText.toLowerCase(Locale.forLanguageTag("tr-TR"));
            if (text.isBlank()) {
                return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                        "SGK dökümü okunamadı. Barkodlu ve net PDF/görsel olup olmadığını kontrol edin.");
            }
            String normalized = normalize(rawText);
            if (!hasExpectedTitle(normalized)) {
                return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                        "SGK hizmet dökümü veya maaş bordrosu başlığı bulunamadı.");
            }
            Matcher start = START.matcher(text);
            while (start.find()) {
                if (parseDate(start.group(2)).isAfter(LocalDate.now().minusMonths(1))) {
                    return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                            "Son bir ay içinde SGK işe giriş kaydı görünüyor. Belgeyi açıp tarihi doğrulayın.");
                }
            }
            Matcher income = INCOME.matcher(text);
            if (!income.find()) {
                return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                        "SGK dökümündeki gelir bilgisi okunamadı. Gelir satırını manuel kontrol edin.");
            }
            BigDecimal incomeValue = new BigDecimal(income.group(2).replace(".", "").replace(',', '.'));
            if (incomeValue.compareTo(limit.multiply(BigDecimal.valueOf(3))) > 0) {
                return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                        "SGK geliri dönem limitinin üç katını aşıyor olabilir. Gelir satırını manuel doğrulayın.");
            }
            if (containsAllWords(normalized, student.getAd()) && containsAllWords(normalized, student.getSoyad())) {
                return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.DOGRULANDI,
                        "SGK belgesi başlığı, ad-soyad ve gelir kontrolleri OCR ile uyumlu görünüyor.");
            }
            return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                    "SGK belgesinde ad-soyad tam eşleşmedi. Öğrenci bilgisiyle karşılaştırın.");
        } catch (ApiException ex) {
            return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI, ex.getMessage());
        } catch (Exception ex) {
            return new BelgeOcrReviewResult(BelgeDogrulamaDurumu.INCELEME_GEREKLI,
                    "SGK OCR çalıştırılamadı; belgeyi manuel inceleyin.");
        }
    }

    private String extract(Path source, String contentType) {
        List<Path> images = new ArrayList<>();
        try {
            if (contentType != null && contentType.contains("pdf")) {
                try (PDDocument document = Loader.loadPDF(source.toFile())) {
                    PDFRenderer renderer = new PDFRenderer(document);
                    for (int i = 0; i < Math.min(document.getNumberOfPages(), 3); i++) {
                        BufferedImage image = renderer.renderImageWithDPI(i, 250);
                        Path png = Files.createTempFile("sgk-ocr-", ".png");
                        ImageIO.write(image, "png", png.toFile()); images.add(png);
                    }
                }
            } else images.add(source);
            StringBuilder result = new StringBuilder();
            for (Path image : images) result.append(runTesseract(image)).append('\n');
            return result.toString();
        } catch (ApiException ex) { throw ex;
        } catch (Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "SGK dökümü OCR ile okunamadı; net bir PDF veya görsel yükleyiniz.");
        } finally { images.stream().filter(path -> !path.equals(source)).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) {} }); }
    }

    private String runTesseract(Path image) throws Exception {
        Process process = new ProcessBuilder(executable, image.toString(), "stdout", "-l", "tur+eng", "--tessdata-dir", tessdata).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException(output);
        return output;
    }

    private LocalDate parseDate(String raw) {
        Matcher m = DATE.matcher(raw); if (!m.find()) return LocalDate.MIN;
        return LocalDate.parse(m.group(1) + "." + m.group(2) + "." + m.group(3), DateTimeFormatter.ofPattern("dd.MM.uuuu"));
    }

    private boolean hasExpectedTitle(String text) {
        return (text.contains("SGK") && (text.contains("HIZMET DOKUMU") || text.contains("HIZMET DOKUM")))
                || text.contains("MAAS BORDRO")
                || text.contains("UCRET BORDRO");
    }

    private boolean containsAllWords(String text, String value) {
        for (String word : normalize(value).split(" ")) {
            if (!word.isBlank() && !text.matches(".*\\b" + Pattern.quote(word) + "\\b.*")) return false;
        }
        return true;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").trim();
    }
}
