package com.sks.sksiskur.web;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.AgreementType;
import com.sks.sksiskur.security.AuthPrincipal;
import com.sks.sksiskur.service.AdminApplicationService;
import com.sks.sksiskur.service.AdminAssignmentService;
import com.sks.sksiskur.service.AdminUserService;
import com.sks.sksiskur.service.AssignmentService;
import com.sks.sksiskur.service.BirimKullaniciService;
import com.sks.sksiskur.service.BasvuruDonemiService;
import com.sks.sksiskur.service.DocumentDownload;
import com.sks.sksiskur.service.TakipService;
import com.sks.sksiskur.service.SozlesmeService;
import com.sks.sksiskur.service.DagitimBirimiService;
import com.sks.sksiskur.web.dto.AdminOzetResponse;
import com.sks.sksiskur.web.dto.AdminUserCreateRequest;
import com.sks.sksiskur.web.dto.AdminUserResponse;
import com.sks.sksiskur.web.dto.AdminUserUpdateRequest;
import com.sks.sksiskur.web.dto.BasvuruDagitimResponse;
import com.sks.sksiskur.web.dto.BirimKullaniciCreateRequest;
import com.sks.sksiskur.web.dto.BirimKullaniciResponse;
import com.sks.sksiskur.web.dto.BirimKullaniciUpdateRequest;
import com.sks.sksiskur.web.dto.AdminTakipOzetResponse;
import com.sks.sksiskur.web.dto.BasvuruResponse;
import com.sks.sksiskur.web.dto.BasvuruDonemiCreateRequest;
import com.sks.sksiskur.web.dto.BasvuruDonemiResponse;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import com.sks.sksiskur.web.dto.DagitimRequest;
import com.sks.sksiskur.web.dto.DagitimSonucResponse;
import com.sks.sksiskur.web.dto.KayitListesiResponse;
import com.sks.sksiskur.web.dto.KayitTurRequest;
import com.sks.sksiskur.web.dto.IzinRaporOgrenciResponse;
import com.sks.sksiskur.web.dto.IadeRequest;
import com.sks.sksiskur.web.dto.ReviewRequest;
import com.sks.sksiskur.web.dto.TakipDonemResponse;
import com.sks.sksiskur.web.dto.TakipKapaliGunKaydetRequest;
import com.sks.sksiskur.web.dto.WorkUnitResponse;
import com.sks.sksiskur.web.dto.SozlesmeKaydetRequest;
import com.sks.sksiskur.web.dto.SozlesmeResponse;
import com.sks.sksiskur.web.dto.DagitimBirimiResponse;
import com.sks.sksiskur.web.dto.DagitimBirimiUpdateRequest;
import com.sks.sksiskur.web.dto.OzelDagitimBirimiRequest;
import com.sks.sksiskur.web.dto.ManuelBirimAtamaRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminApplicationService adminApplicationService;
    private final AssignmentService assignmentService;
    private final TakipService takipService;
    private final BirimKullaniciService birimKullaniciService;
    private final BasvuruDonemiService basvuruDonemiService;
    private final AdminUserService adminUserService;
    private final AdminAssignmentService adminAssignmentService;
    private final SozlesmeService sozlesmeService;
    private final DagitimBirimiService dagitimBirimiService;

    public AdminController(
            AdminApplicationService adminApplicationService,
            AssignmentService assignmentService,
            TakipService takipService,
            BirimKullaniciService birimKullaniciService,
            BasvuruDonemiService basvuruDonemiService,
            AdminUserService adminUserService,
            AdminAssignmentService adminAssignmentService,
            SozlesmeService sozlesmeService,
            DagitimBirimiService dagitimBirimiService
    ) {
        this.adminApplicationService = adminApplicationService;
        this.assignmentService = assignmentService;
        this.takipService = takipService;
        this.birimKullaniciService = birimKullaniciService;
        this.basvuruDonemiService = basvuruDonemiService;
        this.adminUserService = adminUserService;
        this.adminAssignmentService = adminAssignmentService;
        this.sozlesmeService = sozlesmeService;
        this.dagitimBirimiService = dagitimBirimiService;
    }

    @GetMapping("/ozet")
    public AdminOzetResponse ozet(@RequestParam(required = false) Long donemId) {
        return adminApplicationService.ozet(donemId);
    }

    @GetMapping("/basvuru-donemleri")
    public List<BasvuruDonemiResponse> basvuruDonemleri() {
        return basvuruDonemiService.list();
    }

    @PostMapping("/basvuru-donemleri")
    public BasvuruDonemiResponse createBasvuruDonemi(@Valid @RequestBody BasvuruDonemiCreateRequest request) {
        return basvuruDonemiService.create(request);
    }

    @PostMapping("/basvuru-donemleri/{id}/kapat")
    public BasvuruDonemiResponse closeBasvuruDonemi(@PathVariable Long id) {
        return basvuruDonemiService.close(id);
    }

    @PutMapping("/basvuru-donemleri/{id}/gelir-limiti")
    public BasvuruDonemiResponse updateIncomeLimit(@PathVariable Long id, @RequestParam BigDecimal limit) {
        return basvuruDonemiService.updateIncomeLimit(id, limit);
    }

    @GetMapping("/sozlesmeler")
    public List<SozlesmeResponse> sozlesmeler() {
        return sozlesmeService.list();
    }

    @PutMapping("/sozlesmeler/{type}")
    public SozlesmeResponse updateSozlesme(@PathVariable AgreementType type, @Valid @RequestBody SozlesmeKaydetRequest request) {
        return sozlesmeService.update(type, request);
    }

    @GetMapping("/basvurular")
    public List<BasvuruResponse> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long donemId,
            @RequestParam(defaultValue = "false") boolean banaAtanan,
            Authentication authentication
    ) {
        return adminApplicationService.list(donemId, status, q, banaAtanan ? username(authentication) : null);
    }

    @GetMapping("/yoneticiler")
    public List<AdminUserResponse> yoneticiler() {
        return adminUserService.list();
    }

    @PostMapping("/yoneticiler")
    public AdminUserResponse createYonetici(@Valid @RequestBody AdminUserCreateRequest request) {
        return adminUserService.create(request);
    }

    @PutMapping("/yoneticiler/{id}")
    public AdminUserResponse updateYonetici(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request) {
        return adminUserService.update(id, request);
    }

    @PostMapping("/yoneticiler/basvurulari-dagit")
    public BasvuruDagitimResponse assignPendingApplications(@RequestParam(required = false) Long donemId) {
        int assigned = adminAssignmentService.assignUnassigned(basvuruDonemiService.resolveForAdmin(donemId));
        int active = (int) adminUserService.list().stream().filter(AdminUserResponse::aktif).count();
        return new BasvuruDagitimResponse(assigned, active);
    }

    @GetMapping("/basvurular/{id}")
    public BasvuruResponse get(@PathVariable Long id) {
        return adminApplicationService.get(id);
    }

    @PostMapping("/basvurular/{id}/onayla")
    public BasvuruResponse approve(@PathVariable Long id, Authentication authentication) {
        return adminApplicationService.approve(id, username(authentication));
    }

    @PostMapping("/basvurular/{id}/reddet")
    public BasvuruResponse reject(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody ReviewRequest request
    ) {
        return adminApplicationService.reject(id, username(authentication), request);
    }

    @PostMapping("/basvurular/{id}/iade-et")
    public BasvuruResponse returnToStudent(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody IadeRequest request
    ) {
        return adminApplicationService.returnToStudent(id, username(authentication), request.not());
    }

    @GetMapping("/basvurular/{id}/belgeler/{belgeId}")
    public ResponseEntity<?> download(@PathVariable Long id, @PathVariable Long belgeId) {
        DocumentDownload download = adminApplicationService.download(id, belgeId);
        return StudentController.fileResponse(download);
    }

    @GetMapping("/kayit-listesi")
    public KayitListesiResponse kayitListesi() {
        return adminApplicationService.kayitListesi();
    }

    @PutMapping("/basvurular/{id}/kayit")
    public BasvuruResponse setKayit(@PathVariable Long id, @Valid @RequestBody KayitTurRequest request) {
        return adminApplicationService.setKayitTuru(id, request);
    }

    @PostMapping("/kayit-listesi/onayla")
    public KayitListesiResponse onaylaKesinListe(Authentication authentication) {
        return adminApplicationService.onaylaKesinListe(username(authentication));
    }

    @PostMapping("/kayit-listesi/geri-al")
    public KayitListesiResponse geriAlKesinListe() {
        return adminApplicationService.geriAlKesinListe();
    }

    @GetMapping("/birimler")
    public List<WorkUnitResponse> units() {
        return assignmentService.units();
    }

    @GetMapping("/dagitim-birimleri")
    public List<DagitimBirimiResponse> dagitimBirimleri() {
        return dagitimBirimiService.list();
    }

    @PostMapping("/dagitim-birimleri")
    public DagitimBirimiResponse createDagitimBirimi(@Valid @RequestBody OzelDagitimBirimiRequest request) {
        return dagitimBirimiService.createCustom(request);
    }

    @PutMapping("/dagitim-birimleri/{id}")
    public DagitimBirimiResponse updateDagitimBirimi(@PathVariable Long id,
                                                       @Valid @RequestBody DagitimBirimiUpdateRequest request) {
        return dagitimBirimiService.update(id, request);
    }

    @PutMapping("/dagitim/ogrenciler/{id}/birim")
    public DagitimSonucResponse moveStudentManually(@PathVariable Long id,
                                                      @Valid @RequestBody ManuelBirimAtamaRequest request) {
        return assignmentService.moveManually(id, request.birimKodu());
    }

    @GetMapping("/birim-kullanicilar")
    public List<BirimKullaniciResponse> birimKullanicilar() {
        return birimKullaniciService.list();
    }

    @PostMapping("/birim-kullanicilar")
    public BirimKullaniciResponse createBirimKullanici(@Valid @RequestBody BirimKullaniciCreateRequest request) {
        return birimKullaniciService.create(request);
    }

    @PutMapping("/birim-kullanicilar/{id}")
    public BirimKullaniciResponse updateBirimKullanici(
            @PathVariable Long id,
            @Valid @RequestBody BirimKullaniciUpdateRequest request
    ) {
        return birimKullaniciService.update(id, request);
    }

    @DeleteMapping("/birim-kullanicilar/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBirimKullanici(@PathVariable Long id) {
        birimKullaniciService.delete(id);
    }

    @GetMapping("/dagitim")
    public DagitimSonucResponse dagitim() {
        return assignmentService.current();
    }

    @PostMapping("/dagitim")
    public DagitimSonucResponse dagit(@RequestBody(required = false) DagitimRequest request) {
        boolean yeniden = request != null && request.yenidenDagit();
        return assignmentService.distribute(yeniden);
    }

    @GetMapping("/takip")
    public AdminTakipOzetResponse takip(
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay,
            @RequestParam(required = false) String birimKodu,
            @RequestParam(required = false) Long donemId
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return takipService.adminTakipOzet(donemId, birimKodu, month.getYear(), month.getMonthValue());
    }

    @GetMapping("/takip/kapali-gunler")
    public List<LocalDate> kapaliGunler(
            @RequestParam(required = false) Long donemId,
            @RequestParam int yil,
            @RequestParam int ay
    ) {
        return takipService.adminKapaliGunler(donemId, yil, ay);
    }

    @PutMapping("/takip/kapali-gunler")
    public List<LocalDate> saveKapaliGunler(
            @RequestParam(required = false) Long donemId,
            @RequestParam int yil,
            @RequestParam int ay,
            @RequestBody TakipKapaliGunKaydetRequest request
    ) {
        return takipService.saveAdminKapaliGunler(donemId, yil, ay, request);
    }

    @GetMapping("/takip/rapor")
    public BirimAylikRaporResponse takipRapor(
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay,
            @RequestParam(required = false) String birimKodu,
            @RequestParam(required = false) Long donemId
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return takipService.adminMonthlyReport(donemId, birimKodu, month.getYear(), month.getMonthValue());
    }

    @GetMapping("/takip/izin-rapor")
    public List<IzinRaporOgrenciResponse> izinRaporListesi(
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay,
            @RequestParam(required = false) String birimKodu,
            @RequestParam(required = false) Long donemId
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return takipService.leaveAndReportStudents(donemId, birimKodu, month.getYear(), month.getMonthValue());
    }

    @GetMapping("/takip/izin-rapor.csv")
    public ResponseEntity<byte[]> izinRaporCsv(
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay,
            @RequestParam(required = false) String birimKodu,
            @RequestParam(required = false) Long donemId
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return attachment(takipService.leaveAndReportCsv(donemId, birimKodu, month.getYear(), month.getMonthValue()),
                "izinli-raporlu-ogrenciler.csv", "text/csv;charset=UTF-8");
    }

    @GetMapping("/takip/izin-rapor-belgeleri.zip")
    public ResponseEntity<byte[]> izinRaporBelgeleriZip(
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay,
            @RequestParam(required = false) String birimKodu,
            @RequestParam(required = false) Long donemId
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return attachment(takipService.leaveAndReportDocumentsZip(donemId, birimKodu, month.getYear(), month.getMonthValue()),
                "izin-rapor-dilekceleri.zip", "application/zip");
    }

    @GetMapping("/takip/iliskisi-kesilenler.csv")
    public ResponseEntity<byte[]> iliskisiKesilenlerCsv(
            @RequestParam(required = false) Long donemId,
            @RequestParam(required = false) String birimKodu
    ) {
        byte[] csv = takipService.terminatedStudentsCsv(donemId, birimKodu);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=iliskisi-kesilen-ogrenciler.csv")
                .body(csv);
    }

    @GetMapping("/takip/ogrenciler/{basvuruId}")
    public TakipDonemResponse takipOgrenci(
            @PathVariable Long basvuruId,
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay,
            @RequestParam(required = false) Long donemId
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return takipService.adminGet(donemId, basvuruId, month.getYear(), month.getMonthValue());
    }

    @GetMapping("/takip/ogrenciler/{basvuruId}/puantaj/belge")
    public ResponseEntity<?> takipBelge(
            @PathVariable Long basvuruId,
            @RequestParam int yil,
            @RequestParam int ay,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tarih
    ) {
        DocumentDownload download = takipService.adminDownloadBelge(basvuruId, yil, ay, tarih);
        return StudentController.fileResponse(download);
    }

    private YearMonth resolveMonth(Integer yil, Integer ay) {
        if (yil == null || ay == null) {
            return YearMonth.now();
        }
        return YearMonth.of(yil, ay);
    }

    private ResponseEntity<byte[]> attachment(byte[] content, String filename, String contentType) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .body(content);
    }

    private String username(Authentication authentication) {
        return ((AuthPrincipal) authentication.getPrincipal()).getUsername();
    }
}
