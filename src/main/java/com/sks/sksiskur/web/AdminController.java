package com.sks.sksiskur.web;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.AgreementType;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.IslemTuru;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.security.AuthPrincipal;
import com.sks.sksiskur.service.AuditLogService;
import com.sks.sksiskur.service.AdminApplicationService;
import com.sks.sksiskur.service.AdminAssignmentService;
import com.sks.sksiskur.service.AdminUserService;
import com.sks.sksiskur.service.AssignmentService;
import com.sks.sksiskur.service.BirimDuyuruService;
import com.sks.sksiskur.service.BirimKullaniciService;
import com.sks.sksiskur.service.BasvuruDalgaService;
import com.sks.sksiskur.service.BasvuruDonemiService;
import com.sks.sksiskur.service.DocumentDownload;
import com.sks.sksiskur.service.TakipService;
import com.sks.sksiskur.service.SozlesmeService;
import com.sks.sksiskur.service.DagitimBirimiService;
import com.sks.sksiskur.service.AdminExportService;
import com.sks.sksiskur.service.IskurAylikRaporExportService;
import com.sks.sksiskur.service.IskurListeService;
import com.sks.sksiskur.service.ImzaBildirimiService;
import com.sks.sksiskur.service.KesinListeService;
import com.sks.sksiskur.web.dto.ImzaBildirimiGonderResponse;
import com.sks.sksiskur.web.dto.IslemLogPageResponse;
import com.sks.sksiskur.web.dto.AdminOzetResponse;
import com.sks.sksiskur.web.dto.AdminUserCreateRequest;
import com.sks.sksiskur.web.dto.AdminUserResponse;
import com.sks.sksiskur.web.dto.AdminUserUpdateRequest;
import com.sks.sksiskur.web.dto.BasvuruDagitimResponse;
import com.sks.sksiskur.service.OgrenciPanosuDuyuruService;
import com.sks.sksiskur.service.YoneticiPanosuDuyuruService;
import com.sks.sksiskur.web.dto.YoneticiPanosuDuyuruKaydetRequest;
import com.sks.sksiskur.web.dto.YoneticiPanosuDuyuruResponse;
import com.sks.sksiskur.web.dto.BirimDuyuruGonderRequest;
import com.sks.sksiskur.web.dto.BirimDuyuruGonderResponse;
import com.sks.sksiskur.web.dto.BirimDuyuruResponse;
import com.sks.sksiskur.web.dto.BirimKullaniciCreateRequest;
import com.sks.sksiskur.web.dto.BirimKullaniciResponse;
import com.sks.sksiskur.web.dto.BirimKullaniciUpdateRequest;
import com.sks.sksiskur.web.dto.AdminTakipOzetResponse;
import com.sks.sksiskur.web.dto.BasvuruResponse;
import com.sks.sksiskur.web.dto.BelgeYuklemeFiltre;
import com.sks.sksiskur.web.dto.BasvuruDalgaCreateRequest;
import com.sks.sksiskur.web.dto.BasvuruDalgaResponse;
import com.sks.sksiskur.web.dto.BasvuruDonemiCreateRequest;
import com.sks.sksiskur.web.dto.BasvuruDonemiResponse;
import com.sks.sksiskur.web.dto.BirimAylikRaporResponse;
import com.sks.sksiskur.web.dto.DagitimRequest;
import com.sks.sksiskur.web.dto.DagitimSonucResponse;
import com.sks.sksiskur.web.dto.KayitListesiResponse;
import com.sks.sksiskur.web.dto.KayitListeFiltre;
import com.sks.sksiskur.web.dto.KesinListeUploadResponse;
import com.sks.sksiskur.web.dto.SozlesmeImzaBekleyenResponse;
import com.sks.sksiskur.web.dto.SozlesmeImzaPasifTopluRequest;
import com.sks.sksiskur.web.dto.IzinRaporOgrenciResponse;
import com.sks.sksiskur.web.dto.IadeRequest;
import com.sks.sksiskur.web.dto.ReviewRequest;
import com.sks.sksiskur.web.dto.TakipDonemResponse;
import com.sks.sksiskur.web.dto.TakipKapaliGunKaydetRequest;
import com.sks.sksiskur.web.dto.IskurListeResponse;
import com.sks.sksiskur.web.dto.IskurListeUploadResponse;
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
import org.springframework.web.multipart.MultipartFile;

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
    private final BasvuruDalgaService basvuruDalgaService;
    private final AdminUserService adminUserService;
    private final AdminAssignmentService adminAssignmentService;
    private final SozlesmeService sozlesmeService;
    private final DagitimBirimiService dagitimBirimiService;
    private final IskurListeService iskurListeService;
    private final AdminExportService adminExportService;
    private final IskurAylikRaporExportService iskurAylikRaporExportService;
    private final KesinListeService kesinListeService;
    private final ImzaBildirimiService imzaBildirimiService;
    private final BirimDuyuruService birimDuyuruService;
    private final YoneticiPanosuDuyuruService yoneticiPanosuDuyuruService;
    private final OgrenciPanosuDuyuruService ogrenciPanosuDuyuruService;
    private final AuditLogService auditLogService;

    public AdminController(
            AdminApplicationService adminApplicationService,
            AssignmentService assignmentService,
            TakipService takipService,
            BirimKullaniciService birimKullaniciService,
            BasvuruDonemiService basvuruDonemiService,
            BasvuruDalgaService basvuruDalgaService,
            AdminUserService adminUserService,
            AdminAssignmentService adminAssignmentService,
            SozlesmeService sozlesmeService,
            DagitimBirimiService dagitimBirimiService,
            IskurListeService iskurListeService,
            AdminExportService adminExportService,
            IskurAylikRaporExportService iskurAylikRaporExportService,
            KesinListeService kesinListeService,
            ImzaBildirimiService imzaBildirimiService,
            BirimDuyuruService birimDuyuruService,
            YoneticiPanosuDuyuruService yoneticiPanosuDuyuruService,
            OgrenciPanosuDuyuruService ogrenciPanosuDuyuruService,
            AuditLogService auditLogService
    ) {
        this.adminApplicationService = adminApplicationService;
        this.assignmentService = assignmentService;
        this.takipService = takipService;
        this.birimKullaniciService = birimKullaniciService;
        this.basvuruDonemiService = basvuruDonemiService;
        this.basvuruDalgaService = basvuruDalgaService;
        this.adminUserService = adminUserService;
        this.adminAssignmentService = adminAssignmentService;
        this.sozlesmeService = sozlesmeService;
        this.dagitimBirimiService = dagitimBirimiService;
        this.iskurListeService = iskurListeService;
        this.adminExportService = adminExportService;
        this.iskurAylikRaporExportService = iskurAylikRaporExportService;
        this.kesinListeService = kesinListeService;
        this.imzaBildirimiService = imzaBildirimiService;
        this.birimDuyuruService = birimDuyuruService;
        this.yoneticiPanosuDuyuruService = yoneticiPanosuDuyuruService;
        this.ogrenciPanosuDuyuruService = ogrenciPanosuDuyuruService;
        this.auditLogService = auditLogService;
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
    public BasvuruDonemiResponse createBasvuruDonemi(
            @Valid @RequestBody BasvuruDonemiCreateRequest request,
            Authentication authentication
    ) {
        BasvuruDonemiResponse created = basvuruDonemiService.create(request);
        auditLogService.logAdmin(authentication, IslemTuru.DONEM_AC, "DONEM", created.id(),
                "Başvuru dönemi açıldı: " + created.ad(), null);
        return created;
    }

    @PostMapping("/basvuru-donemleri/{id}/kapat")
    public BasvuruDonemiResponse closeBasvuruDonemi(@PathVariable Long id, Authentication authentication) {
        BasvuruDonemiResponse closed = basvuruDonemiService.close(id);
        auditLogService.logAdmin(authentication, IslemTuru.DONEM_KAPAT, "DONEM", closed.id(),
                "Başvuru dönemi kapatıldı: " + closed.ad(), null);
        return closed;
    }

    @PutMapping("/basvuru-donemleri/{id}/gelir-limiti")
    public BasvuruDonemiResponse updateIncomeLimit(@PathVariable Long id, @RequestParam BigDecimal limit) {
        return basvuruDonemiService.updateIncomeLimit(id, limit);
    }

    @GetMapping("/basvuru-donemleri/{id}/dalgalar")
    public List<BasvuruDalgaResponse> basvuruDalgalar(@PathVariable Long id) {
        return basvuruDalgaService.list(id);
    }

    @PostMapping("/basvuru-donemleri/{id}/dalgalar/yeni-tur")
    public BasvuruDalgaResponse yeniBasvuruTuru(
            @PathVariable Long id,
            @Valid @RequestBody BasvuruDalgaCreateRequest request,
            Authentication authentication
    ) {
        return basvuruDalgaService.createNext(id, request, username(authentication));
    }

    @PostMapping("/basvuru-donemleri/dalgalar/{dalgaId}/tamamla")
    public BasvuruDalgaResponse tamamlaBasvuruTuru(
            @PathVariable Long dalgaId,
            Authentication authentication
    ) {
        return basvuruDalgaService.tamamla(dalgaId, username(authentication));
    }

    @GetMapping("/basvuru-donemleri/{id}/iskur-listesi")
    public IskurListeResponse iskurListesi(@PathVariable Long id) {
        return iskurListeService.get(id);
    }

    @PostMapping(value = "/basvuru-donemleri/{id}/iskur-listesi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IskurListeUploadResponse uploadIskurListesi(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return iskurListeService.upload(id, file, principal.getUsername());
    }

    @GetMapping("/basvuru-donemleri/{id}/iskur-listesi.xlsx")
    public ResponseEntity<byte[]> iskurListesiExcel(@PathVariable Long id) {
        return excelAttachment(adminExportService.iskurListesiExcel(id), "iskur-basvuru-listesi.xlsx");
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
            @RequestParam(required = false) DocumentType belgeTipi,
            @RequestParam(required = false) BelgeYuklemeFiltre belgeYukleme,
            Authentication authentication
    ) {
        return adminApplicationService.list(
                donemId, status, q, banaAtanan ? username(authentication) : null, belgeTipi, belgeYukleme);
    }

    @GetMapping("/basvurular.xlsx")
    public ResponseEntity<byte[]> basvurularExcel(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long donemId,
            @RequestParam(defaultValue = "false") boolean banaAtanan,
            @RequestParam(required = false) DocumentType belgeTipi,
            @RequestParam(required = false) BelgeYuklemeFiltre belgeYukleme,
            Authentication authentication
    ) {
        return excelAttachment(adminExportService.basvurularExcel(
                donemId,
                status,
                q,
                banaAtanan ? username(authentication) : null,
                belgeTipi,
                belgeYukleme
        ), "basvurular.xlsx");
    }

    @GetMapping("/basvurular-belgeler.zip")
    public ResponseEntity<byte[]> basvurularBelgeBazindaZip(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long donemId,
            @RequestParam(defaultValue = "false") boolean banaAtanan,
            @RequestParam(required = false) DocumentType belgeTipi,
            @RequestParam(required = false) BelgeYuklemeFiltre belgeYukleme,
            Authentication authentication
    ) {
        String filename = "basvurular-" + belgeTipi.name().toLowerCase() + ".zip";
        return attachment(adminExportService.basvurularBelgeBazindaZip(
                donemId,
                status,
                q,
                banaAtanan ? username(authentication) : null,
                belgeTipi,
                belgeYukleme
        ), filename, "application/zip");
    }

    @GetMapping("/yoneticiler")
    public List<AdminUserResponse> yoneticiler(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return adminUserService.list(principal.userId());
    }

    @PostMapping("/yoneticiler")
    public AdminUserResponse createYonetici(
            @Valid @RequestBody AdminUserCreateRequest request,
            Authentication authentication
    ) {
        return adminUserService.create(request, username(authentication));
    }

    @PutMapping("/yoneticiler/{id}")
    public AdminUserResponse updateYonetici(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request,
            Authentication authentication
    ) {
        return adminUserService.update(id, request, username(authentication));
    }

    @PostMapping("/yoneticiler/basvurulari-dagit")
    public BasvuruDagitimResponse assignPendingApplications(
            @RequestParam(required = false) Long donemId,
            Authentication authentication
    ) {
        int assigned = adminAssignmentService.assignUnassigned(basvuruDonemiService.resolveForAdmin(donemId));
        int active = (int) adminUserService.list(null).stream().filter(AdminUserResponse::aktif).count();
        auditLogService.logAdmin(authentication, IslemTuru.BASVURU_YONETICI_DAGITIM, "DONEM", donemId,
                "Atamasız başvurular yöneticilere dağıtıldı", assigned + " başvuru, " + active + " aktif yönetici");
        return new BasvuruDagitimResponse(assigned, active);
    }

    @GetMapping("/basvurular/{id}")
    public BasvuruResponse get(@PathVariable Long id) {
        return adminApplicationService.get(id);
    }

    @PostMapping("/basvurular/{id}/ocr-kontrol")
    public BasvuruResponse refreshOcr(@PathVariable Long id) {
        return adminApplicationService.refreshOcr(id);
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
    public KayitListesiResponse kayitListesi(@RequestParam(required = false) KayitListeFiltre filtre) {
        return adminApplicationService.kayitListesi(filtre);
    }

    @GetMapping("/kayit-listesi.xlsx")
    public ResponseEntity<byte[]> kayitListesiExcel() {
        return excelAttachment(adminExportService.kayitListesiExcel(), "kayit-listesi.xlsx");
    }

    @PostMapping(value = "/kayit-listesi/kesin-liste", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KesinListeUploadResponse uploadKesinListe(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return kesinListeService.upload(
                basvuruDonemiService.requireActive().getId(),
                file,
                username(authentication)
        );
    }

    @PostMapping("/kayit-listesi/onayla")
    public KayitListesiResponse onaylaKesinListe(Authentication authentication) {
        return adminApplicationService.onaylaKesinListe(username(authentication));
    }

    @PostMapping("/kayit-listesi/geri-al")
    public KayitListesiResponse geriAlKesinListe(Authentication authentication) {
        return adminApplicationService.geriAlKesinListe(username(authentication));
    }

    @PostMapping("/kayit-listesi/imza-bildirimi-gonder")
    public ImzaBildirimiGonderResponse gonderImzaBildirimi(Authentication authentication) {
        return imzaBildirimiService.gonder(username(authentication));
    }

    @GetMapping("/kayit-listesi/sozlesme-imza-bekleyen")
    public List<SozlesmeImzaBekleyenResponse> sozlesmeImzaBekleyen(@RequestParam(required = false) Long donemId) {
        Long resolved = donemId != null ? donemId : basvuruDonemiService.requireActive().getId();
        return imzaBildirimiService.listSozlesmeImzaBekleyen(resolved);
    }

    @PostMapping("/kayit-listesi/sozlesme-imza/{basvuruId}/imzalandi")
    public void sozlesmeImzalandi(@PathVariable Long basvuruId, Authentication authentication) {
        imzaBildirimiService.markSozlesmeImzalandi(basvuruId, username(authentication));
    }

    @PostMapping("/kayit-listesi/sozlesme-imza/pasif")
    public int sozlesmeImzaPasif(
            @Valid @RequestBody SozlesmeImzaPasifTopluRequest request,
            Authentication authentication
    ) {
        return imzaBildirimiService.markSozlesmeImzaPasif(request.basvuruIds(), username(authentication));
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
    public DagitimSonucResponse moveStudentManually(
            @PathVariable Long id,
            @Valid @RequestBody ManuelBirimAtamaRequest request,
            Authentication authentication
    ) {
        DagitimSonucResponse result = assignmentService.moveManually(id, request.birimKodu());
        auditLogService.logAdmin(authentication, IslemTuru.BIRIM_ATAMA_DEGISTIR, "BASVURU", id,
                "Öğrenci birimi manuel değiştirildi", request.birimKodu());
        return result;
    }

    @GetMapping("/pano-duyurulari")
    public List<YoneticiPanosuDuyuruResponse> panoDuyurulari(@RequestParam(required = false) Boolean aktif) {
        if (Boolean.TRUE.equals(aktif)) {
            return yoneticiPanosuDuyuruService.listActive();
        }
        return yoneticiPanosuDuyuruService.listAll();
    }

    @PostMapping("/pano-duyurulari")
    public YoneticiPanosuDuyuruResponse createPanoDuyurusu(
            Authentication authentication,
            @Valid @RequestBody YoneticiPanosuDuyuruKaydetRequest request
    ) {
        return yoneticiPanosuDuyuruService.create(username(authentication), request);
    }

    @PutMapping("/pano-duyurulari/{id}")
    public YoneticiPanosuDuyuruResponse updatePanoDuyurusu(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody YoneticiPanosuDuyuruKaydetRequest request
    ) {
        return yoneticiPanosuDuyuruService.update(id, username(authentication), request);
    }

    @DeleteMapping("/pano-duyurulari/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePanoDuyurusu(Authentication authentication, @PathVariable Long id) {
        yoneticiPanosuDuyuruService.delete(id, username(authentication));
    }

    @GetMapping("/ogrenci-duyurulari")
    public List<YoneticiPanosuDuyuruResponse> ogrenciDuyurulari(@RequestParam(required = false) Boolean aktif) {
        if (Boolean.TRUE.equals(aktif)) {
            return ogrenciPanosuDuyuruService.listActive();
        }
        return ogrenciPanosuDuyuruService.listAll();
    }

    @PostMapping("/ogrenci-duyurulari")
    public YoneticiPanosuDuyuruResponse createOgrenciDuyurusu(
            Authentication authentication,
            @Valid @RequestBody YoneticiPanosuDuyuruKaydetRequest request
    ) {
        return ogrenciPanosuDuyuruService.create(username(authentication), request);
    }

    @PutMapping("/ogrenci-duyurulari/{id}")
    public YoneticiPanosuDuyuruResponse updateOgrenciDuyurusu(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody YoneticiPanosuDuyuruKaydetRequest request
    ) {
        return ogrenciPanosuDuyuruService.update(id, username(authentication), request);
    }

    @DeleteMapping("/ogrenci-duyurulari/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOgrenciDuyurusu(Authentication authentication, @PathVariable Long id) {
        ogrenciPanosuDuyuruService.delete(id, username(authentication));
    }

    @GetMapping("/birim-duyurulari")
    public List<BirimDuyuruResponse> birimDuyurulari() {
        return birimDuyuruService.listForAdmin();
    }

    @PostMapping("/birim-duyurulari")
    public BirimDuyuruGonderResponse gonderBirimDuyurusu(
            Authentication authentication,
            @Valid @RequestBody BirimDuyuruGonderRequest request
    ) {
        return birimDuyuruService.send(username(authentication), request);
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
    public DagitimSonucResponse dagit(
            @RequestBody(required = false) DagitimRequest request,
            Authentication authentication
    ) {
        boolean yeniden = request != null && request.yenidenDagit();
        DagitimSonucResponse result = assignmentService.distribute(yeniden);
        auditLogService.logAdmin(authentication, IslemTuru.BIRIM_DAGITIM, "DONEM", null,
                yeniden ? "Birim dağıtımı yeniden yapıldı" : "Birim dağıtımı yapıldı",
                result.atanan() + " atandı, " + result.atanamayan() + " atanamadı");
        return result;
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
            @RequestBody TakipKapaliGunKaydetRequest request,
            Authentication authentication
    ) {
        List<LocalDate> saved = takipService.saveAdminKapaliGunler(donemId, yil, ay, request);
        auditLogService.logAdmin(authentication, IslemTuru.KAPALI_GUN_KAYDET, "DONEM", donemId,
                "Kapalı günler kaydedildi: " + yil + "-" + String.format("%02d", ay),
                saved.size() + " gün");
        return saved;
    }

    @GetMapping("/islem-loglari")
    public IslemLogPageResponse islemLoglari(
            @RequestParam(required = false) IslemTuru tur,
            @RequestParam(required = false) Role rol,
            @RequestParam(required = false) String kullanici,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return auditLogService.list(tur, rol, kullanici, from, to, page, size);
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

    @GetMapping("/takip.xlsx")
    public ResponseEntity<byte[]> takipOzetExcel(
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay,
            @RequestParam(required = false) String birimKodu,
            @RequestParam(required = false) Long donemId
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return excelAttachment(
                adminExportService.takipOzetExcel(donemId, birimKodu, month.getYear(), month.getMonthValue()),
                "takip-ozeti.xlsx");
    }

    @GetMapping("/takip/rapor.xlsx")
    public ResponseEntity<byte[]> takipRaporExcel(
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay,
            @RequestParam(required = false) String birimKodu,
            @RequestParam(required = false) Long donemId
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return excelAttachment(
                adminExportService.takipRaporExcel(donemId, birimKodu, month.getYear(), month.getMonthValue()),
                "ek6-puantaj-raporu.xlsx");
    }

    @GetMapping("/takip/iskur-paketi.xlsx")
    public ResponseEntity<byte[]> takipIskurPaketiExcel(
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay,
            @RequestParam(required = false) String birimKodu,
            @RequestParam(required = false) Long donemId
    ) {
        YearMonth month = resolveMonth(yil, ay);
        String scope = (birimKodu == null || birimKodu.isBlank()) ? "tum-birimler" : birimKodu;
        String filename = String.format(
                "iskur-odeme_%s_%d-%02d.xlsm",
                scope,
                month.getYear(),
                month.getMonthValue()
        );
        return attachment(
                iskurAylikRaporExportService.export(donemId, birimKodu, month.getYear(), month.getMonthValue()),
                filename,
                "application/vnd.ms-excel.sheet.macroEnabled.12"
        );
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

    @GetMapping("/takip/izin-rapor.xlsx")
    public ResponseEntity<byte[]> izinRaporExcel(
            @RequestParam(required = false) Integer yil,
            @RequestParam(required = false) Integer ay,
            @RequestParam(required = false) String birimKodu,
            @RequestParam(required = false) Long donemId
    ) {
        YearMonth month = resolveMonth(yil, ay);
        return excelAttachment(
                adminExportService.izinRaporExcel(donemId, birimKodu, month.getYear(), month.getMonthValue()),
                "izinli-raporlu-ogrenciler.xlsx");
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

    @GetMapping("/takip/iliskisi-kesilenler.xlsx")
    public ResponseEntity<byte[]> iliskisiKesilenlerExcel(
            @RequestParam(required = false) Long donemId,
            @RequestParam(required = false) String birimKodu
    ) {
        return excelAttachment(adminExportService.iliskisiKesilenlerExcel(donemId, birimKodu),
                "iliskisi-kesilen-ogrenciler.xlsx");
    }

    @PostMapping("/takip/ogrenciler/{basvuruId}/onayla")
    public TakipDonemResponse takipOnayla(
            @PathVariable Long basvuruId,
            @RequestParam int yil,
            @RequestParam int ay,
            @RequestParam(required = false) Long donemId,
            Authentication authentication
    ) {
        TakipDonemResponse saved = takipService.adminApprove(donemId, basvuruId, yil, ay);
        auditLogService.logAdmin(authentication, IslemTuru.TAKIP_ONAY, "BASVURU", basvuruId,
                "Aylık takip onaylandı: " + yil + "-" + String.format("%02d", ay), null);
        return saved;
    }

    @PostMapping("/takip/onayla-gonderilenler")
    public int takipOnaylaGonderilenler(
            @RequestParam int yil,
            @RequestParam int ay,
            @RequestParam String birimKodu,
            @RequestParam(required = false) Long donemId,
            Authentication authentication
    ) {
        int count = takipService.adminApproveSubmitted(donemId, birimKodu, yil, ay);
        auditLogService.logAdmin(authentication, IslemTuru.TAKIP_ONAY, "BIRIM", null,
                "Toplu takip onayı: " + birimKodu + " " + yil + "-" + String.format("%02d", ay),
                count + " öğrenci");
        return count;
    }

    @PostMapping("/takip/ogrenciler/{basvuruId}/iade")
    public TakipDonemResponse takipIade(
            @PathVariable Long basvuruId,
            @RequestParam int yil,
            @RequestParam int ay,
            @RequestParam(required = false) Long donemId,
            Authentication authentication
    ) {
        TakipDonemResponse saved = takipService.adminReturn(donemId, basvuruId, yil, ay);
        auditLogService.logAdmin(authentication, IslemTuru.TAKIP_IADE, "BASVURU", basvuruId,
                "Aylık takip birime iade edildi: " + yil + "-" + String.format("%02d", ay), null);
        return saved;
    }

    @PostMapping("/takip/iade-gonderilenler")
    public int takipIadeGonderilenler(
            @RequestParam int yil,
            @RequestParam int ay,
            @RequestParam String birimKodu,
            @RequestParam(required = false) Long donemId,
            Authentication authentication
    ) {
        int count = takipService.adminReturnSubmitted(donemId, birimKodu, yil, ay);
        auditLogService.logAdmin(authentication, IslemTuru.TAKIP_IADE, "BIRIM", null,
                "Toplu takip iadesi: " + birimKodu + " " + yil + "-" + String.format("%02d", ay),
                count + " öğrenci");
        return count;
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

    private ResponseEntity<byte[]> excelAttachment(byte[] content, String filename) {
        return attachment(content, filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    private String username(Authentication authentication) {
        return ((AuthPrincipal) authentication.getPrincipal()).getUsername();
    }
}
