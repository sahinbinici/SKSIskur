package com.sks.sksiskur.service;

import com.sks.sksiskur.domain.ApplicationStatus;
import com.sks.sksiskur.domain.Basvuru;
import com.sks.sksiskur.domain.BasvuruBelgesi;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.web.dto.BasvuruResponse;
import com.sks.sksiskur.web.dto.StudentProfileResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class DtoMapper {

    public StudentProfileResponse toProfile(Student student) {
        return toProfile(student, false, true, null);
    }

    public StudentProfileResponse toProfile(
            Student student,
            boolean demoOgrenci,
            boolean iskurBasvuruyaUygun,
            String iskurBasvuruEngelMesaji
    ) {
        return new StudentProfileResponse(
                student.getId(),
                student.getOgrenciNo(),
                student.getTcKimlikNo(),
                student.getAd(),
                student.getSoyad(),
                student.getAdSoyad(),
                student.getUyruk(),
                student.getDogumYeri(),
                student.getDogumTarihi(),
                student.getCinsiyet(),
                student.getEgitimDerecesi(),
                student.getKayitTarihi(),
                student.getOgrenimDurumu(),
                student.getFakulte(),
                student.getBolum(),
                student.getProgram(),
                student.getSinif(),
                student.getDurumu(),
                student.getEposta(),
                student.getGsm(),
                student.getAdres(),
                student.getIl(),
                student.getIlce(),
                student.getFotoUrl(),
                student.getDanisman(),
                demoOgrenci,
                iskurBasvuruyaUygun,
                iskurBasvuruEngelMesaji
        );
    }

    public BasvuruResponse toBasvuru(Basvuru basvuru) {
        return new BasvuruResponse(
                basvuru.getId(),
                basvuru.getStatus(),
                basvuru.getKayitTuru(),
                basvuru.getKayitTarihi(),
                basvuru.getIban(),
                basvuru.getHesapSahibi(),
                basvuru.getBankaSubeKodu(),
                basvuru.getHesapNumarasi(),
                basvuru.getIletisimEposta(),
                basvuru.getIletisimGsm(),
                basvuru.getAylikGelir(),
                basvuru.getAdminNotu(),
                basvuru.getInceleyenAdmin(),
                basvuru.getAtananAdmin() == null ? null : basvuru.getAtananAdmin().getUsername(),
                basvuru.getOlusturmaTarihi(),
                basvuru.getGuncellemeTarihi(),
                basvuru.getGonderimTarihi(),
                basvuru.getIncelemeTarihi(),
                basvuru.isLockedForStudent(),
                basvuru.getKesinListede(),
                basvuru.isAtamaBildirimiOkundu(),
                basvuru.isImzaBildirimiGonderildi(),
                basvuru.getImzaBildirimiMesaji(),
                basvuru.getImzaBildirimiGonderimTarihi(),
                basvuru.isImzaBildirimiOkundu(),
                basvuru.isImzaBildirimiEpostaGonderildi(),
                basvuru.isSozlesmeImzalandi(),
                basvuru.isSozlesmeImzaPasif(),
                basvuru.getAtananBirimKodu(),
                basvuru.getAtananBirimAdi(),
                basvuru.getAtamaTuru(),
                basvuru.getAtamaTarihi(),
                basvuru.getBasvuruDonemi() == null ? null : basvuru.getBasvuruDonemi().getId(),
                basvuru.getBasvuruDonemi() == null ? null : basvuru.getBasvuruDonemi().getAd(),
                basvuru.getBasvuruDonemi() != null && basvuru.getBasvuruDonemi().isAktif(),
                toProfile(basvuru.getStudent()),
                basvuru.getBelgeler().stream()
                        .sorted(Comparator.comparing(b -> b.getBelgeTipi().name()))
                        .map(this::toBelge)
                        .toList()
        );
    }

    public BasvuruResponse.BelgeResponse toBelge(BasvuruBelgesi belge) {
        return new BasvuruResponse.BelgeResponse(
                belge.getId(),
                belge.getBelgeTipi(),
                belge.getBelgeTipi().getLabel(),
                belge.getHaneUyesiAdi(),
                belge.getOrijinalAd(),
                belge.getIcerikTipi(),
                belge.getBoyutByte(),
                belge.getDogrulamaDurumu(),
                belge.getDogrulamaNotu(),
                belge.getYuklemeTarihi()
        );
    }

    public String statusLabel(ApplicationStatus status) {
        return switch (status) {
            case DRAFT -> "Taslak";
            case SUBMITTED -> "İncelemede";
            case RETURNED -> "Evrak tamamlaması bekleniyor";
            case APPROVED -> "Onaylandı";
            case REJECTED -> "Reddedildi";
        };
    }
}
