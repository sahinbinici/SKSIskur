package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.AgreementDocument;
import com.sks.sksiskur.domain.AgreementType;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.AgreementDocumentRepository;
import com.sks.sksiskur.repository.StudentRepository;
import com.sks.sksiskur.web.dto.OgrenciSozlesmeResponse;
import com.sks.sksiskur.web.dto.SozlesmeKaydetRequest;
import com.sks.sksiskur.web.dto.SozlesmeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class SozlesmeService {

    private final AgreementDocumentRepository agreementDocumentRepository;
    private final StudentRepository studentRepository;

    public SozlesmeService(
            AgreementDocumentRepository agreementDocumentRepository,
            StudentRepository studentRepository
    ) {
        this.agreementDocumentRepository = agreementDocumentRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public List<OgrenciSozlesmeResponse> ogrenciSozlesmeleri(String ogrenciNo) {
        Student student = requireStudent(ogrenciNo);
        return documents().stream().map(document -> toStudentResponse(document, student)).toList();
    }

    @Transactional
    public List<OgrenciSozlesmeResponse> kabulEt(String ogrenciNo, AgreementType type) {
        Student student = requireStudent(ogrenciNo);
        List<AgreementDocument> documents = documents();
        AgreementDocument document = documents.stream()
                .filter(item -> item.getType() == type)
                .findFirst()
                .orElseThrow();

        if (type == AgreementType.ISKUR_SOZLESMESI && !isAccepted(student, documentFor(documents, AgreementType.KVKK))) {
            throw new ApiException(HttpStatus.CONFLICT, "Önce KVKK aydınlatma metnini kabul etmelisiniz.");
        }

        if (type == AgreementType.KVKK) {
            student.setKvkkSozlesmeVersiyonu(document.getVersiyon());
            student.setKvkkOnayTarihi(Instant.now());
        } else {
            student.setIskurSozlesmeVersiyonu(document.getVersiyon());
            student.setIskurSozlesmeOnayTarihi(Instant.now());
        }
        studentRepository.save(student);
        return documents.stream().map(item -> toStudentResponse(item, student)).toList();
    }

    @Transactional
    public void tumSozlesmelerKabulEdildiMi(String ogrenciNo) {
        Student student = requireStudent(ogrenciNo);
        boolean allAccepted = documents().stream().allMatch(document -> isAccepted(student, document));
        if (!allAccepted) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Başvuru işlemlerine devam etmek için KVKK ve İŞKUR sözleşmesini kabul etmelisiniz.");
        }
    }

    @Transactional
    public List<SozlesmeResponse> list() {
        return documents().stream().map(this::toResponse).toList();
    }

    @Transactional
    public SozlesmeResponse update(AgreementType type, SozlesmeKaydetRequest request) {
        AgreementDocument document = documentFor(documents(), type);
        String title = request.baslik().trim();
        String content = request.icerik().trim();
        if (!title.equals(document.getBaslik()) || !content.equals(document.getIcerik())) {
            document.setBaslik(title);
            document.setIcerik(content);
            document.setVersiyon(document.getVersiyon() + 1);
            agreementDocumentRepository.save(document);
        }
        return toResponse(document);
    }

    private List<AgreementDocument> documents() {
        return Arrays.stream(AgreementType.values()).map(this::ensureDocument).toList();
    }

    private AgreementDocument ensureDocument(AgreementType type) {
        return agreementDocumentRepository.findById(type).orElseGet(() -> {
            AgreementDocument document = new AgreementDocument();
            document.setType(type);
            document.setBaslik(type == AgreementType.KVKK ? "KVKK Aydınlatma Metni" : "İŞKUR Gençlik Programı Sözleşmesi");
            document.setIcerik(type == AgreementType.KVKK ? defaultKvkkContent() : defaultIskurContent());
            document.setVersiyon(1);
            return agreementDocumentRepository.save(document);
        });
    }

    private AgreementDocument documentFor(List<AgreementDocument> documents, AgreementType type) {
        return documents.stream().filter(document -> document.getType() == type).findFirst().orElseThrow();
    }

    private Student requireStudent(String ogrenciNo) {
        return studentRepository.findByOgrenciNo(ogrenciNo)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Öğrenci kaydı bulunamadı."));
    }

    private boolean isAccepted(Student student, AgreementDocument document) {
        return switch (document.getType()) {
            case KVKK -> student.getKvkkSozlesmeVersiyonu() != null
                    && student.getKvkkSozlesmeVersiyonu() == document.getVersiyon();
            case ISKUR_SOZLESMESI -> student.getIskurSozlesmeVersiyonu() != null
                    && student.getIskurSozlesmeVersiyonu() == document.getVersiyon();
        };
    }

    private OgrenciSozlesmeResponse toStudentResponse(AgreementDocument document, Student student) {
        Instant acceptedAt = document.getType() == AgreementType.KVKK
                ? student.getKvkkOnayTarihi()
                : student.getIskurSozlesmeOnayTarihi();
        return new OgrenciSozlesmeResponse(
                document.getType(), document.getBaslik(), document.getIcerik(), document.getVersiyon(),
                isAccepted(student, document), acceptedAt
        );
    }

    private SozlesmeResponse toResponse(AgreementDocument document) {
        return new SozlesmeResponse(
                document.getType(), document.getBaslik(), document.getIcerik(), document.getVersiyon(), document.getGuncellemeTarihi()
        );
    }

    private String defaultKvkkContent() {
        return "KVKK AYDINLATMA METNİ\n\nBu alan, üniversitenin güncel kişisel verilerin korunması aydınlatma metni için hazırlanmıştır. Yönetim panelinden yayımlanacak nihai metni buraya ekleyiniz.\n\nÖğrenci; başvuru sürecinde paylaştığı kişisel verilerin, İŞKUR Gençlik Programı başvurusunun yürütülmesi, değerlendirilmesi ve yasal yükümlülüklerin yerine getirilmesi amaçlarıyla işlenebileceği konusunda bilgilendirildiğini kabul eder.";
    }

    private String defaultIskurContent() {
        return "İŞKUR GENÇLİK PROGRAMI SÖZLEŞMESİ\n\nBu alan, İŞKUR Gençlik Programı için kullanılacak güncel sözleşme metni için hazırlanmıştır. Yönetim panelinden yayımlanacak nihai metni buraya ekleyiniz.\n\nÖğrenci; program kurallarına, çalışma planına ve üniversite tarafından bildirilecek yükümlülüklere uyacağını kabul eder.";
    }
}
