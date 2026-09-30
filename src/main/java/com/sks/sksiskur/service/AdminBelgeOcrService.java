package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.DocumentType;
import com.sks.sksiskur.repository.BasvuruBelgesiRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

@Service
public class AdminBelgeOcrService {

    private final FileStorageService fileStorageService;
    private final BelgeOcrService belgeOcrService;
    private final SgkOcrService sgkOcrService;
    private final BasvuruBelgesiRepository belgeRepository;

    public AdminBelgeOcrService(
            FileStorageService fileStorageService,
            BelgeOcrService belgeOcrService,
            SgkOcrService sgkOcrService,
            BasvuruBelgesiRepository belgeRepository
    ) {
        this.fileStorageService = fileStorageService;
        this.belgeOcrService = belgeOcrService;
        this.sgkOcrService = sgkOcrService;
        this.belgeRepository = belgeRepository;
    }

    @Transactional
    public void reviewPendingDocuments(Basvuru basvuru) {
        for (BasvuruBelgesi belge : basvuru.getBelgeler()) {
            if (belge.getDogrulamaDurumu() != null || !supportsOcr(belge.getBelgeTipi())) {
                continue;
            }
            Path path = fileStorageService.resolve(belge.getSaklamaYolu());
            BelgeOcrReviewResult result = reviewDocument(belge, path, basvuru);
            belge.setDogrulamaDurumu(result.durum());
            belge.setDogrulamaNotu(result.ipucu());
            belgeRepository.save(belge);
        }
    }

    @Transactional
    public void refreshDocuments(Basvuru basvuru) {
        for (BasvuruBelgesi belge : basvuru.getBelgeler()) {
            if (!supportsOcr(belge.getBelgeTipi())) {
                continue;
            }
            Path path = fileStorageService.resolve(belge.getSaklamaYolu());
            BelgeOcrReviewResult result = reviewDocument(belge, path, basvuru);
            belge.setDogrulamaDurumu(result.durum());
            belge.setDogrulamaNotu(result.ipucu());
            belgeRepository.save(belge);
        }
    }

    private BelgeOcrReviewResult reviewDocument(BasvuruBelgesi belge, Path path, Basvuru basvuru) {
        if (belge.getBelgeTipi() == DocumentType.SGK_DOKUMU) {
            return sgkOcrService.review(path, belge.getIcerikTipi(),
                    basvuru.getBasvuruDonemi().getAylikGelirLimiti(), basvuru.getStudent());
        }
        return belgeOcrService.review(path, belge.getIcerikTipi(), basvuru.getStudent(), belge.getBelgeTipi());
    }

    private boolean supportsOcr(DocumentType type) {
        return type == DocumentType.SGK_DOKUMU
                || type == DocumentType.KIMLIK_BELGESI
                || type == DocumentType.OGRENCI_BELGESI
                || type == DocumentType.ADLI_SICIL
                || type == DocumentType.IKAMETGAH;
    }
}
