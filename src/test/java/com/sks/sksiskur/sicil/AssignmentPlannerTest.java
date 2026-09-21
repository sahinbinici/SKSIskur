package com.sks.sksiskur.sicil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentPlannerTest {

    @Test
    void facultyOverflowGoesToOtherUnits() {
        WorkUnit egitim = new WorkUnit("35", "Gaziantep Eğitim Fakültesi", "EĞİTİM FAKÜLTESİ", 1660, "ISICIL");
        WorkUnit sks = new WorkUnit("4", "Sağlık Kültür ve Spor Daire Başkanlığı", "SKS", 1649, "ISICIL");
        WorkUnit kutuphane = new WorkUnit("6", "Kütüphane Dökümantasyon Daire Başkanlığı", "KÜTÜPHANE", 1650, "ISICIL");
        Faculty faculty = new Faculty(7, "Gaziantep Eğitim Fakültesi", 1660, List.of("Eğitim Fakültesi"));

        List<AssignmentPlanner.Candidate> students = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            students.add(new AssignmentPlanner.Candidate((long) i, "Gaziantep Eğitim Fakültesi", "Sınıf Öğretmenliği"));
        }

        AssignmentPlanner.Plan plan = new AssignmentPlanner().plan(
                students,
                List.of(egitim, sks, kutuphane),
                List.of(faculty),
                10,
                new Random(42)
        );

        long toEgitim = plan.placements().stream().filter(p -> p.birimKodu().equals("35")).count();
        long facultyPriority = plan.placements().stream().filter(p -> "FAKULTE".equals(p.tur())).count();
        long random = plan.placements().stream().filter(p -> "RASTGELE".equals(p.tur())).count();

        assertEquals(20, plan.placements().size());
        assertEquals(10, toEgitim);
        assertEquals(10, facultyPriority);
        assertEquals(10, random);
        assertTrue(plan.unassignedIds().isEmpty());
    }

    @Test
    void teknikBilimlerMatchesMyo() {
        WorkUnit myo = new WorkUnit("155", "Teknik Bilimler MYO Müdürlüğü", "TEKNİK BİLİMLER MYO", 6273, "ISICIL");
        Faculty faculty = new Faculty(51, "Teknik Bilimler M.Y.O.", 6273, List.of("Teknik Bilimler Meslek Yüksekokulu"));
        var match = new FacultyUnitMatcher().preferredUnit(
                "Teknik Bilimler Meslek Yüksekokulu",
                "Bilgisayar Teknolojileri Bölümü",
                List.of(faculty),
                List.of(myo)
        );
        assertTrue(match.isPresent());
        assertEquals("155", match.get().kod());
    }
}
