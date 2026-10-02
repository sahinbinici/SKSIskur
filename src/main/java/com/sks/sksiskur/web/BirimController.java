package com.sks.sksiskur.web;

import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.security.AuthPrincipal;
import com.sks.sksiskur.service.AuditLogService;
import com.sks.sksiskur.service.DocumentDownload;
import com.sks.sksiskur.service.BirimDuyuruService;
import com.sks.sksiskur.service.AdminExportService;
import com.sks.sksiskur.service.TakipService;
import com.sks.sksiskur.web.dto.BirimAyGonderResponse;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import com.sks.sksiskur.web.dto.BirimDuyuruInboxResponse;
import com.sks.sksiskur.web.dto.BirimOgrenciResponse;
import com.sks.sksiskur.web.dto.EkuantKaydetRequest;
import com.sks.sksiskur.web.dto.PuantajKaydetRequest;
import com.sks.sksiskur.web.dto.TakipAyRequest;
import com.sks.sksiskur.web.dto.TakipDonemResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
    private final AdminExportService adminExportService;
    private final BirimDuyuruService birimDuyuruService;
    private final AuditLogService auditLogService;

    public BirimController(
            TakipService takipService,
            AdminExportService adminExportService,
            BirimDuyuruService birimDuyuruService,
            AuditLogService auditLogService
    ) {
        this.takipService = takipService;
        this.adminExportService = adminExportService;
        this.birimDuyuruService = birimDuyuruService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/duyurular")
    public List<BirimDuyuruInboxResponse> duyurular(Authentication authentication) {
        return birimDuyuruService.listForBirim(birimKodu(authentication));
    }

    @PostMapping("/duyurular/{id}/okundu")
    public BirimDuyuruInboxResponse markDuyuruOkundu(Authentication authentication, @PathVariable Long id) {
        return birimDuyuruService.markRead(birimKodu(authentication), id);
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

    @GetMapping("/rapor.xlsx")
    public ResponseEntity<byte[]> monthlyReportExcel(
            Authentication authentication,
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay
    ) {
        YearMonth month = resolveMonth(yil, ay);
        String kod = birimKodu(authentication);
        byte[] content = adminExportService.birimTakipRaporExcel(kod, month.getYear(), month.getMonthValue());
        String filename = "ek6-puantaj_" + kod + "_" + month.getYear() + "-"
                + String.format("%02d", month.getMonthValue()) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .body(content);
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
        TakipDonemResponse saved = takipService.saveEkuant(birimKodu(authentication), basvuruId, request);
        auditLogService.logBirim(authentication, birimKodu(authentication), IslemTuru.EKUANT_KAYDET, "BASVURU", basvuruId,
                "Ekuant kaydedildi: " + request.yil() + "-" + String.format("%02d", request.ay()),
                request.gunler() == null ? null : request.gunler().size() + " gün");
        return saved;
    }

    @PutMapping("/ogrenciler/{basvuruId}/puantaj")
    public TakipDonemResponse savePuantaj(
            Authentication authentication,
            @PathVariable Long basvuruId,
            @RequestBody PuantajKaydetRequest request
    ) {
        TakipDonemResponse saved = takipService.savePuantaj(birimKodu(authentication), basvuruId, request);
        auditLogService.logBirim(authentication, birimKodu(authentication), IslemTuru.PUANTAJ_KAYDET, "BASVURU", basvuruId,
                "Puantaj kaydedildi: " + request.yil() + "-" + String.format("%02d", request.ay()),
                request.kayitlar() == null ? null : request.kayitlar().size() + " kayıt");
        return saved;
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

    @PostMapping("/takip/gonder")
    public BirimAyGonderResponse submitAy(
            Authentication authentication,
            @RequestBody TakipAyRequest request
    ) {
        String birimKodu = birimKodu(authentication);
        BirimAyGonderResponse result = takipService.submitBirimAy(birimKodu, request.yil(), request.ay());
        auditLogService.logBirim(authentication, birimKodu, IslemTuru.TAKIP_GONDER, "BIRIM", null,
                "Aylık takip toplu gönderildi: " + request.yil() + "-" + String.format("%02d", request.ay()),
                result.gonderilenOgrenci() + " öğrenci");
        return result;
    }

    @PostMapping("/ogrenciler/{basvuruId}/gonder")
    public TakipDonemResponse submit(
            Authentication authentication,
            @PathVariable Long basvuruId,
            @RequestBody TakipAyRequest request
    ) {
        TakipDonemResponse saved = takipService.submit(birimKodu(authentication), basvuruId, request.yil(), request.ay());
        auditLogService.logBirim(authentication, birimKodu(authentication), IslemTuru.TAKIP_GONDER, "BASVURU", basvuruId,
                "Aylık takip gönderildi: " + request.yil() + "-" + String.format("%02d", request.ay()), null);
        return saved;
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
