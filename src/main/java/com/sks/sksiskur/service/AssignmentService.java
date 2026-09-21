package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruDonemi;
import com.sks.sksiskur.domain.KayitListesi;
import com.sks.sksiskur.domain.KayitTuru;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.repository.BasvuruRepository;
import com.sks.sksiskur.repository.KayitListesiRepository;
import com.sks.sksiskur.sicil.AssignmentPlanner;
import com.sks.sksiskur.sicil.Faculty;
import com.sks.sksiskur.sicil.SicilUnitCatalog;
import com.sks.sksiskur.sicil.WorkUnit;
import com.sks.sksiskur.web.dto.DagitimSonucResponse;
import com.sks.sksiskur.web.dto.WorkUnitResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssignmentService {

    private final BasvuruRepository basvuruRepository;
    private final KayitListesiRepository kayitListesiRepository;
    private final SicilUnitCatalog sicilUnitCatalog;
    private final DagitimBirimiService dagitimBirimiService;
    private final AssignmentPlanner planner = new AssignmentPlanner();
    private final int quota;
    private final BasvuruDonemiService basvuruDonemiService;

    public AssignmentService(
            BasvuruRepository basvuruRepository,
            KayitListesiRepository kayitListesiRepository,
            SicilUnitCatalog sicilUnitCatalog,
            DagitimBirimiService dagitimBirimiService,
            @Value("${app.dagitim.kontenjan:10}") int quota,
            BasvuruDonemiService basvuruDonemiService
    ) {
        this.basvuruRepository = basvuruRepository;
        this.kayitListesiRepository = kayitListesiRepository;
        this.sicilUnitCatalog = sicilUnitCatalog;
        this.dagitimBirimiService = dagitimBirimiService;
        this.quota = quota;
        this.basvuruDonemiService = basvuruDonemiService;
    }

    public List<WorkUnitResponse> units() {
        return dagitimBirimiService.allUnits().stream()
                .map(configured -> configured.unit())
                .map(u -> new WorkUnitResponse(u.kod(), u.displayName(), u.kamuKod(), u.kaynak()))
                .toList();
    }

    @Transactional
    public DagitimSonucResponse current() {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        return toResponse(kesinOgrenciler(donem), Instant.now(), donem);
    }

    @Transactional
    public DagitimSonucResponse distribute(boolean yenidenDagit) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        if (!kesinListeOnayli(donem)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Birim dağıtımı ancak kesin liste onaylandıktan sonra yapılabilir.");
        }
        List<Basvuru> approved = new ArrayList<>(kesinOgrenciler(donem));
        if (approved.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dağıtılacak kesin kayıtlı öğrenci yok.");
        }

        List<Basvuru> targets = yenidenDagit
                ? approved
                : approved.stream().filter(b -> !b.isAssigned()).toList();
        if (targets.isEmpty()) {
            return toResponse(approved, Instant.now(), donem);
        }

        List<DagitimBirimiService.ConfiguredUnit> configuredUnits = dagitimBirimiService.activeUnits();
        List<WorkUnit> units = configuredUnits.stream().map(DagitimBirimiService.ConfiguredUnit::unit).toList();
        List<Faculty> faculties = sicilUnitCatalog.faculties();
        if (units.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Birim listesi boş geldi. Sicil bağlantısını kontrol edin.");
        }

        if (yenidenDagit) {
            targets.forEach(Basvuru::clearAssignment);
        }

        List<AssignmentPlanner.Candidate> candidates = targets.stream()
                .map(b -> new AssignmentPlanner.Candidate(
                        b.getId(),
                        b.getStudent().getFakulte(),
                        b.getStudent().getBolum()
                ))
                .toList();
        Map<String, Integer> quotas = configuredUnits.stream().collect(Collectors.toMap(
                unit -> unit.unit().kod(), DagitimBirimiService.ConfiguredUnit::kontenjan));
        AssignmentPlanner.Plan plan = planner.plan(candidates, units, faculties, quotas, new Random());
        Map<Long, Basvuru> byId = targets.stream().collect(Collectors.toMap(Basvuru::getId, Function.identity()));
        Instant now = Instant.now();
        for (AssignmentPlanner.Placement placement : plan.placements()) {
            Basvuru basvuru = byId.get(placement.basvuruId());
            basvuru.setAtananBirimKodu(placement.birimKodu());
            basvuru.setAtananBirimAdi(placement.birimAdi());
            basvuru.setAtamaTuru(placement.tur());
            basvuru.setAtamaTarihi(now);
        }
        basvuruRepository.saveAll(targets);
        return toResponse(kesinOgrenciler(donem), now, donem);
    }

    @Transactional
    public DagitimSonucResponse moveManually(Long basvuruId, String birimKodu) {
        BasvuruDonemi donem = basvuruDonemiService.requireActive();
        if (!kesinListeOnayli(donem)) {
            throw new ApiException(HttpStatus.CONFLICT, "Manuel taşıma için kesin liste önce onaylanmalıdır.");
        }
        Basvuru basvuru = basvuruRepository.findDetailedById(basvuruId)
                .filter(item -> item.getStatus() == ApplicationStatus.APPROVED
                        && item.getKayitTuru() == KayitTuru.KESIN
                        && item.getBasvuruDonemi().getId().equals(donem.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Kesin kayıtlı öğrenci bulunamadı."));
        DagitimBirimiService.ConfiguredUnit target = dagitimBirimiService.activeUnits().stream()
                .filter(unit -> unit.unit().kod().equals(birimKodu.trim()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Hedef birim dağıtıma açık değil."));
        if (target.unit().kod().equals(basvuru.getAtananBirimKodu())) {
            return toResponse(kesinOgrenciler(donem), Instant.now(), donem);
        }
        long occupied = kesinOgrenciler(donem).stream()
                .filter(item -> target.unit().kod().equals(item.getAtananBirimKodu()))
                .count();
        if (occupied >= target.kontenjan()) {
            throw new ApiException(HttpStatus.CONFLICT, "Hedef birimin kontenjanı dolu.");
        }
        basvuru.setAtananBirimKodu(target.unit().kod());
        basvuru.setAtananBirimAdi(target.unit().displayName());
        basvuru.setAtamaTuru("MANUEL");
        basvuru.setAtamaTarihi(Instant.now());
        basvuruRepository.save(basvuru);
        return toResponse(kesinOgrenciler(donem), Instant.now(), donem);
    }

    private List<Basvuru> kesinOgrenciler(BasvuruDonemi donem) {
        return basvuruRepository.findByStatusAndKayitTuruAndBasvuruDonemiIdOrderByStudentSoyadAscStudentAdAsc(
                ApplicationStatus.APPROVED, KayitTuru.KESIN, donem.getId());
    }

    private boolean kesinListeOnayli(BasvuruDonemi donem) {
        return kayitListesiRepository.findByBasvuruDonemiId(donem.getId())
                .map(KayitListesi::isKesinOnaylandi)
                .orElse(false);
    }

    private DagitimSonucResponse toResponse(List<Basvuru> approved, Instant atamaTarihi, BasvuruDonemi donem) {
        Map<String, DagitimSonucResponse.BirimOzet> grouped = new LinkedHashMap<>();
        List<DagitimBirimiService.ConfiguredUnit> configuredUnits = dagitimBirimiService.allUnits();
        Map<String, Integer> quotas = configuredUnits.stream().collect(Collectors.toMap(
                unit -> unit.unit().kod(), DagitimBirimiService.ConfiguredUnit::kontenjan));
        for (DagitimBirimiService.ConfiguredUnit configured : configuredUnits.stream()
                .filter(DagitimBirimiService.ConfiguredUnit::dagitimaAcik)
                .sorted(Comparator.comparing(unit -> unit.unit().displayName(), String.CASE_INSENSITIVE_ORDER))
                .toList()) {
            WorkUnit unit = configured.unit();
            grouped.put(unit.kod(), new DagitimSonucResponse.BirimOzet(
                    unit.kod(), unit.displayName(), configured.kontenjan(), 0, configured.kontenjan(), new ArrayList<>()
            ));
        }

        List<DagitimSonucResponse.AtamaSatir> unassigned = new ArrayList<>();
        int fakulte = 0;
        int rastgele = 0;
        for (Basvuru basvuru : approved) {
            DagitimSonucResponse.AtamaSatir row = new DagitimSonucResponse.AtamaSatir(
                    basvuru.getId(),
                    basvuru.getStudent().getOgrenciNo(),
                    basvuru.getStudent().getAdSoyad(),
                    basvuru.getStudent().getFakulte(),
                    basvuru.getAtamaTuru(),
                    basvuru.getAtananBirimAdi()
            );
            if (!basvuru.isAssigned()) {
                unassigned.add(row);
                continue;
            }
            if ("FAKULTE".equals(basvuru.getAtamaTuru())) {
                fakulte++;
            } else {
                rastgele++;
            }
            DagitimSonucResponse.BirimOzet current = grouped.get(basvuru.getAtananBirimKodu());
            if (current == null) {
                current = new DagitimSonucResponse.BirimOzet(
                        basvuru.getAtananBirimKodu(),
                        basvuru.getAtananBirimAdi(),
                        quotas.getOrDefault(basvuru.getAtananBirimKodu(), 0),
                        0,
                        quotas.getOrDefault(basvuru.getAtananBirimKodu(), 0),
                        new ArrayList<>()
                );
                grouped.put(basvuru.getAtananBirimKodu(), current);
            }
            current.ogrenciler().add(row);
        }

        List<DagitimSonucResponse.BirimOzet> birimler = grouped.values().stream()
                .map(item -> new DagitimSonucResponse.BirimOzet(
                        item.kod(),
                        item.ad(),
                        item.kontenjan(),
                        item.ogrenciler().size(),
                        Math.max(0, item.kontenjan() - item.ogrenciler().size()),
                        item.ogrenciler()
                ))
                .toList();
        int atanan = approved.size() - unassigned.size();
        int yedek = (int) basvuruRepository.countByStatusAndKayitTuruAndBasvuruDonemiId(
                ApplicationStatus.APPROVED, KayitTuru.YEDEK, donem.getId());
        return new DagitimSonucResponse(
                configuredUnits.stream().filter(DagitimBirimiService.ConfiguredUnit::dagitimaAcik)
                        .mapToInt(DagitimBirimiService.ConfiguredUnit::kontenjan).sum(),
                approved.size(),
                atanan,
                fakulte,
                rastgele,
                unassigned.size(),
                atamaTarihi,
                kesinListeOnayli(donem),
                yedek,
                birimler,
                unassigned
        );
    }
}
