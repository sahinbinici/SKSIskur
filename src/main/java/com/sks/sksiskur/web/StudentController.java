package com.sks.sksiskur.web;

import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.domain.AgreementType;
import com.sks.sksiskur.security.AuthPrincipal;
import com.sks.sksiskur.service.DocumentDownload;
import com.sks.sksiskur.service.StudentApplicationService;
import com.sks.sksiskur.service.SozlesmeService;
import com.sks.sksiskur.web.dto.BasvuruKaydetRequest;
import com.sks.sksiskur.web.dto.BasvuruResponse;
import com.sks.sksiskur.web.dto.StudentProfileResponse;
import com.sks.sksiskur.web.dto.OgrenciSozlesmeResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentApplicationService studentApplicationService;
    private final SozlesmeService sozlesmeService;

    public StudentController(StudentApplicationService studentApplicationService, SozlesmeService sozlesmeService) {
        this.studentApplicationService = studentApplicationService;
        this.sozlesmeService = sozlesmeService;
    }

    @GetMapping("/profil")
    public StudentProfileResponse profile(Authentication authentication) {
        return studentApplicationService.profile(ogrenciNo(authentication));
    }

    @GetMapping("/sozlesmeler")
    public List<OgrenciSozlesmeResponse> agreements(Authentication authentication) {
        return sozlesmeService.ogrenciSozlesmeleri(ogrenciNo(authentication));
    }

    @PostMapping("/sozlesmeler/{type}/kabul")
    public List<OgrenciSozlesmeResponse> acceptAgreement(Authentication authentication, @PathVariable AgreementType type) {
        return sozlesmeService.kabulEt(ogrenciNo(authentication), type);
    }

    @GetMapping("/basvuru")
    public BasvuruResponse getApplication(Authentication authentication) {
        return studentApplicationService.getOrCreate(ogrenciNo(authentication));
    }

    @PutMapping("/basvuru")
    public BasvuruResponse saveDraft(Authentication authentication, @Valid @RequestBody BasvuruKaydetRequest request) {
        return studentApplicationService.saveDraft(ogrenciNo(authentication), request);
    }

    @PostMapping("/basvuru/gonder")
    public BasvuruResponse submit(Authentication authentication, @Valid @RequestBody BasvuruKaydetRequest request) {
        return studentApplicationService.submit(ogrenciNo(authentication), request);
    }

    @PostMapping(value = "/basvuru/belgeler", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BasvuruResponse upload(
            Authentication authentication,
            @RequestParam DocumentType belgeTipi,
            @RequestParam(required = false) String haneUyesiAdi,
            @RequestParam MultipartFile file
    ) {
        return studentApplicationService.uploadDocument(ogrenciNo(authentication), belgeTipi, haneUyesiAdi, file);
    }

    @DeleteMapping("/basvuru/belgeler/{belgeId}")
    public BasvuruResponse deleteDocument(Authentication authentication, @PathVariable Long belgeId) {
        return studentApplicationService.deleteDocument(ogrenciNo(authentication), belgeId);
    }

    @GetMapping("/basvuru/belgeler/{belgeId}")
    public ResponseEntity<?> download(Authentication authentication, @PathVariable Long belgeId) {
        DocumentDownload download = studentApplicationService.downloadDocument(ogrenciNo(authentication), belgeId);
        return fileResponse(download);
    }

    @PostMapping("/basvuru/atama-bildirimi/okundu")
    public BasvuruResponse markAssignmentNoticeRead(Authentication authentication) {
        return studentApplicationService.markAtamaBildirimiOkundu(ogrenciNo(authentication));
    }

    private String ogrenciNo(Authentication authentication) {
        return ((AuthPrincipal) authentication.getPrincipal()).getUsername();
    }

    static ResponseEntity<?> fileResponse(DocumentDownload download) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.resource());
    }
}
