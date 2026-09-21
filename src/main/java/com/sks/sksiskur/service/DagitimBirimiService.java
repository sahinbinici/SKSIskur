package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.DagitimBirimi;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.DagitimBirimiRepository;
import com.sks.sksiskur.sicil.SicilUnitCatalog;
import com.sks.sksiskur.sicil.WorkUnit;
import com.sks.sksiskur.web.dto.DagitimBirimiResponse;
import com.sks.sksiskur.web.dto.DagitimBirimiUpdateRequest;
import com.sks.sksiskur.web.dto.OzelDagitimBirimiRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DagitimBirimiService {
    public record ConfiguredUnit(WorkUnit unit, int kontenjan, boolean dagitimaAcik) { }

    private final DagitimBirimiRepository repository;
    private final SicilUnitCatalog sicilUnitCatalog;
    private final int defaultQuota;

    public DagitimBirimiService(DagitimBirimiRepository repository, SicilUnitCatalog sicilUnitCatalog,
                                @Value("${app.dagitim.kontenjan:10}") int defaultQuota) {
        this.repository = repository;
        this.sicilUnitCatalog = sicilUnitCatalog;
        this.defaultQuota = defaultQuota;
    }

    @Transactional
    public List<DagitimBirimiResponse> list() {
        syncSicilUnits();
        return repository.findAllByOrderByBirimAdiAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<ConfiguredUnit> allUnits() {
        syncSicilUnits();
        return repository.findAllByOrderByBirimAdiAsc().stream().map(this::toConfigured).toList();
    }

    @Transactional
    public List<ConfiguredUnit> activeUnits() {
        return allUnits().stream().filter(unit -> unit.dagitimaAcik() && unit.kontenjan() > 0).toList();
    }

    @Transactional
    public DagitimBirimiResponse createCustom(OzelDagitimBirimiRequest request) {
        syncSicilUnits();
        String code = request.kod().trim();
        if (repository.existsByBirimKodu(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu birim kodu zaten kullanılıyor.");
        }
        DagitimBirimi unit = new DagitimBirimi();
        unit.setBirimKodu(code);
        unit.setBirimAdi(request.ad().trim());
        unit.setKaynak("OZEL");
        unit.setKontenjan(request.kontenjan());
        unit.setDagitimaAcik(request.dagitimaAcik());
        return toResponse(repository.save(unit));
    }

    @Transactional
    public DagitimBirimiResponse update(Long id, DagitimBirimiUpdateRequest request) {
        DagitimBirimi unit = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dağıtım birimi bulunamadı."));
        unit.setKontenjan(request.kontenjan());
        unit.setDagitimaAcik(request.dagitimaAcik());
        return toResponse(repository.save(unit));
    }

    private void syncSicilUnits() {
        for (WorkUnit remote : sicilUnitCatalog.workUnits()) {
            if (remote.kod() == null || remote.kod().isBlank()) continue;
            DagitimBirimi local = repository.findByBirimKodu(remote.kod()).orElseGet(() -> {
                DagitimBirimi created = new DagitimBirimi();
                created.setBirimKodu(remote.kod());
                created.setKontenjan(defaultQuota);
                created.setDagitimaAcik(true);
                return created;
            });
            if (!"OZEL".equals(local.getKaynak())) {
                local.setBirimAdi(remote.displayName());
                local.setKamuKodu(remote.kamuKod());
                local.setKaynak(remote.kaynak() == null ? "SICIL" : remote.kaynak());
                repository.save(local);
            }
        }
    }

    private ConfiguredUnit toConfigured(DagitimBirimi unit) {
        return new ConfiguredUnit(new WorkUnit(unit.getBirimKodu(), unit.getBirimAdi(), unit.getBirimAdi(),
                unit.getKamuKodu(), unit.getKaynak()), unit.getKontenjan(), unit.isDagitimaAcik());
    }

    private DagitimBirimiResponse toResponse(DagitimBirimi unit) {
        return new DagitimBirimiResponse(unit.getId(), unit.getBirimKodu(), unit.getBirimAdi(), unit.getKamuKodu(),
                unit.getKaynak(), unit.getKontenjan(), unit.isDagitimaAcik(), "OZEL".equals(unit.getKaynak()));
    }
}
