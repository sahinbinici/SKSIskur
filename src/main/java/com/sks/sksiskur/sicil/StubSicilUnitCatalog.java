package com.sks.sksiskur.sicil;

import com.sks.sksiskur.config.DemoAccounts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "sicil.enabled", havingValue = "false")
public class StubSicilUnitCatalog implements SicilUnitCatalog {

    @Override
    public List<WorkUnit> workUnits() {
        return DemoAccounts.units();
    }

    @Override
    public List<Faculty> faculties() {
        return List.of(
                new Faculty(7, "Gaziantep Eğitim Fakültesi", 1660, List.of("Eğitim Fakültesi", "EGTFAK", "Gaziantep Eğitim Fakültesi")),
                new Faculty(51, "Teknik Bilimler M.Y.O.", 6273, List.of("Teknik Bilimler Meslek Yüksekokulu", "TBMYO"))
        );
    }

    @Override
    public java.util.Optional<WorkUnit> authenticate(String birimKodu, String sifre) {
        return DemoAccounts.authenticate(birimKodu, sifre);
    }
}
