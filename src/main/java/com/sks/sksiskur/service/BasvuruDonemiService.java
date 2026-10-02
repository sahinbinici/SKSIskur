package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruDonemiRepository;
import com.sks.sksiskur.repository.IskurBasvuruKaydiRepository;
import com.sks.sksiskur.web.dto.BasvuruDonemiCreateRequest;
import com.sks.sksiskur.web.dto.BasvuruDonemiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

@Service
public class BasvuruDonemiService {

    private final BasvuruDonemiRepository repository;
    private final IskurBasvuruKaydiRepository iskurBasvuruKaydiRepository;
    private final BasvuruDalgaService basvuruDalgaService;

    public BasvuruDonemiService(
            BasvuruDonemiRepository repository,
            IskurBasvuruKaydiRepository iskurBasvuruKaydiRepository,
            BasvuruDalgaService basvuruDalgaService
    ) {
        this.repository = repository;
        this.iskurBasvuruKaydiRepository = iskurBasvuruKaydiRepository;
        this.basvuruDalgaService = basvuruDalgaService;
    }

    @Transactional(readOnly = true)
    public List<BasvuruDonemiResponse> list() {
        return repository.findAllByOrderByOlusturmaTarihiDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BasvuruDonemi requireActive() {
        return repository.findFirstByAktifTrueOrderByOlusturmaTarihiDesc()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Aktif bir başvuru dönemi bulunmuyor."));
    }

    @Transactional(readOnly = true)
    public BasvuruDonemi resolveForAdmin(Long id) {
        if (id == null) {
            return requireActive();
        }
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Başvuru dönemi bulunamadı."));
    }

    @Transactional
    public BasvuruDonemiResponse create(BasvuruDonemiCreateRequest request) {
        if (repository.findFirstByAktifTrueOrderByOlusturmaTarihiDesc().isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Yeni dönem açmadan önce açık olan başvuru dönemi kapatılmalıdır.");
        }
        if (request.ogrenciBitisTarihi().isBefore(request.ogrenciBaslangicTarihi())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Başvuru bitiş tarihi başlangıç tarihinden önce olamaz.");
        }
        BasvuruDonemi donem = new BasvuruDonemi();
        donem.setAd(request.ad().trim());
        donem.setAktif(true);
        donem.setOgrenciBaslangicTarihi(request.ogrenciBaslangicTarihi());
        donem.setOgrenciBitisTarihi(request.ogrenciBitisTarihi());
        donem.setAylikGelirLimiti(request.aylikGelirLimiti().setScale(2));
        BasvuruDonemi saved = repository.save(donem);
        basvuruDalgaService.ensureInitialDalga(saved);
        return toResponse(saved);
    }

    @Transactional
    public BasvuruDonemiResponse close(Long id) {
        BasvuruDonemi donem = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Başvuru dönemi bulunamadı."));
        if (!donem.isAktif()) {
            return toResponse(donem);
        }
        donem.setAktif(false);
        donem.setKapanisTarihi(Instant.now());
        return toResponse(repository.save(donem));
    }

    @Transactional
    public BasvuruDonemiResponse updateIncomeLimit(Long id, BigDecimal limit) {
        if (limit == null || limit.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Geçerli bir aylık gelir limiti giriniz.");
        }
        BasvuruDonemi donem = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Başvuru dönemi bulunamadı."));
        donem.setAylikGelirLimiti(limit.setScale(2));
        return toResponse(repository.save(donem));
    }

    public BasvuruDonemiResponse toResponse(BasvuruDonemi donem) {
        long iskurKayitSayisi = iskurBasvuruKaydiRepository.countByBasvuruDonemiId(donem.getId());
        return new BasvuruDonemiResponse(
                donem.getId(), donem.getAd(), donem.isAktif(), donem.getOlusturmaTarihi(), donem.getKapanisTarihi(),
                donem.getOgrenciBaslangicTarihi(), donem.getOgrenciBitisTarihi(), isStudentAccessOpen(donem),
                donem.getAylikGelirLimiti(),
                iskurKayitSayisi > 0,
                iskurKayitSayisi,
                donem.getIskurListeYuklemeTarihi()
        );
    }

    @Transactional(readOnly = true)
    public void assertStudentAccessOpen() {
        if (!isStudentAccessOpen(requireActive())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Öğrenci başvuru giriş süresi şu anda açık değil.");
        }
    }

    @Transactional(readOnly = true)
    public boolean isStudentAccessOpen() {
        return repository.findFirstByAktifTrueOrderByOlusturmaTarihiDesc().map(this::isStudentAccessOpen).orElse(false);
    }

    private boolean isStudentAccessOpen(BasvuruDonemi donem) {
        return basvuruDalgaService.isStudentAccessOpen(donem);
    }
}
