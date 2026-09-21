package com.sks.sksiskur.web;

import com.sks.sksiskur.security.AuthPrincipal;
import com.sks.sksiskur.service.DocumentDownload;
import com.sks.sksiskur.service.TakipService;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import com.sks.sksiskur.web.dto.BirimOgrenciResponse;
import com.sks.sksiskur.web.dto.EkuantKaydetRequest;
import com.sks.sksiskur.web.dto.PuantajKaydetRequest;
import com.sks.sksiskur.web.dto.TakipAyRequest;
import com.sks.sksiskur.web.dto.TakipDonemResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/birim")
public class BirimController {

    private final TakipService takipService;

    public BirimController(TakipService takipService) {
        this.takipService = takipService;
    }

    @GetMapping("/ogrenciler")
    public List<BirimOgrenciResponse> list(Authentication authentication) {
        return takipService.listStudents(birimKodu(authentication));
    }

    @GetMapping("/rapor")
    public BirimAylikRaporResponse monthlyReport(
            Authentication authentication,
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return takipService.monthlyReport(birimKodu(authentication), month.getYear(), month.getMonthValue());
    }

    @GetMapping("/ogrenciler/{basvuruId}/takip")
    public TakipDonemResponse get(
            Authentication authentication,
            @PathVariable Long basvuruId,
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return takipService.getOrCreate(birimKodu(authentication), basvuruId, month.getYear(), month.getMonthValue());
    }

    @PutMapping("/ogrenciler/{basvuruId}/ekuant")
    public TakipDonemResponse saveEkuant(
            Authentication authentication,
            @PathVariable Long basvuruId,
            @Valid @RequestBody EkuantKaydetRequest request
    ) {
        return takipService.saveEkuant(birimKodu(authentication), basvuruId, request);
    }

    @PutMapping("/ogrenciler/{basvuruId}/puantaj")
    public TakipDonemResponse savePuantaj(
            Authentication authentication,
            @PathVariable Long basvuruId,
            @RequestBody PuantajKaydetRequest request
    ) {
        return takipService.savePuantaj(birimKodu(authentication), basvuruId, request);
    }

    @PostMapping(value = "/ogrenciler/{basvuruId}/puantaj/belge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TakipDonemResponse upload(
            Authentication authentication,
            @PathVariable Long basvuruId,
            @RequestParam int yil,
            @RequestParam int ay,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tarih,
            @RequestParam MultipartFile file
    ) {
        return takipService.uploadBelge(birimKodu(authentication), basvuruId, yil, ay, tarih, file);
    }

    @GetMapping("/ogrenciler/{basvuruId}/puantaj/belge")
    public ResponseEntity<?> download(
            Authentication authentication,
            @PathVariable Long basvuruId,
            @RequestParam int yil,
            @RequestParam int ay,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tarih
    ) {
        DocumentDownload download = takipService.downloadBelge(birimKodu(authentication), basvuruId, yil, ay, tarih);
        return StudentController.fileResponse(download);
    }

    @PostMapping("/ogrenciler/{basvuruId}/gonder")
    public TakipDonemResponse submit(
            Authentication authentication,
            @PathVariable Long basvuruId,
            @RequestBody TakipAyRequest request
    ) {
        return takipService.submit(birimKodu(authentication), basvuruId, request.yil(), request.ay());
    }

    private String birimKodu(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        if (principal.birimKodu() == null || principal.birimKodu().isBlank()) {
            return principal.getUsername();
        }
        return principal.birimKodu();
    }

    private YearMonth resolveMonth(Integer yil, Integer ay) {
        if (yil == null || ay == null) {
            return YearMonth.now();
        }
        return YearMonth.of(yil, ay);
    }
}
