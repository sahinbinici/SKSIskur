package com.sks.sksiskur.service;

import com.sks.sksiskur.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new IllegalStateException("Yükleme dizini oluşturulamadı: " + this.root, ex);
        }
    }

    public StoredFile store(Long basvuruId, String documentType, MultipartFile file) {
        return store("basvuru-" + basvuruId, documentType, file);
    }

    public StoredFile store(String folder, String documentType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dosya seçilmedi.");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "belge";
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Yalnızca PDF, JPG ve PNG dosyaları yüklenebilir.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.isBlank() && !ALLOWED_CONTENT_TYPES.contains(contentType) && !"image/jpg".equals(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dosya türü kabul edilmiyor.");
        }

        try {
            Path folderPath = root.resolve(folder.replaceAll("[^a-zA-Z0-9._-]", "_"));
            Files.createDirectories(folderPath);
            String storedName = documentType.toLowerCase(Locale.ROOT) + "_" + UUID.randomUUID() + "." + extension;
            Path target = folderPath.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String relative = root.relativize(target).toString().replace('\\', '/');
            return new StoredFile(original, relative, contentType.isBlank() ? guessContentType(extension) : contentType, file.getSize());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Dosya kaydedilemedi.");
        }
    }

    public Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Geçersiz dosya yolu.");
        }
        return path;
    }

    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String guessContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    public record StoredFile(String originalName, String relativePath, String contentType, long size) {
    }
}
