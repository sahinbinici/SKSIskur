package com.sks.sksiskur.config;

import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.repository.BasvuruDonemiRepository;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.KayitListesiRepository;
import com.sks.sksiskur.service.BasvuruDalgaService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
@Component
@Order(10)
public class BasvuruDonemiInitializer implements CommandLineRunner {

    private final BasvuruDonemiRepository donemRepository;
    private final BasvuruRepository basvuruRepository;
    private final KayitListesiRepository kayitListesiRepository;
    private final BasvuruDalgaService basvuruDalgaService;

    public BasvuruDonemiInitializer(
            BasvuruDonemiRepository donemRepository,
            BasvuruRepository basvuruRepository,
            KayitListesiRepository kayitListesiRepository,
            BasvuruDalgaService basvuruDalgaService
    ) {
        this.donemRepository = donemRepository;
        this.basvuruRepository = basvuruRepository;
        this.kayitListesiRepository = kayitListesiRepository;
        this.basvuruDalgaService = basvuruDalgaService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        BasvuruDonemi donem = donemRepository.findFirstByAktifTrueOrderByOlusturmaTarihiDesc().orElse(null);
        if (donem == null && donemRepository.count() == 0) {
            donem = new BasvuruDonemi();
            donem.setAd(Year.now() + " Başvuru Dönemi");
            donem.setAktif(true);
            donem.setAylikGelirLimiti(new java.math.BigDecimal("10000.00"));
            donem = donemRepository.save(donem);
        }
        if (donem != null) {
            BasvuruDonemi activeDonem = donem;
            basvuruRepository.findAll().stream()
                    .filter(basvuru -> basvuru.getBasvuruDonemi() == null)
                    .forEach(basvuru -> basvuru.setBasvuruDonemi(activeDonem));
            kayitListesiRepository.findAll().stream()
                    .filter(liste -> liste.getBasvuruDonemi() == null)
                    .forEach(liste -> liste.setBasvuruDonemi(activeDonem));
        }
        for (BasvuruDonemi period : donemRepository.findAll()) {
            basvuruDalgaService.ensureInitialDalga(period);
            kayitListesiRepository.findByBasvuruDonemiId(period.getId()).ifPresent(liste ->
                    basvuruDalgaService.migrateKayitListesiIfNeeded(
                            period,
                            liste.isKesinOnaylandi(),
                            liste.isImzaBildirimiGonderildi(),
                            liste.getOnayTarihi(),
                            liste.getOnaylayanAdmin(),
                            liste.getKesinListeYuklemeTarihi(),
                            liste.getKesinListeYukleyenAdmin(),
                            liste.getImzaBildirimiGonderimTarihi(),
                            liste.getImzaBildirimiGonderenAdmin()
                    ));
        }
    }
}
