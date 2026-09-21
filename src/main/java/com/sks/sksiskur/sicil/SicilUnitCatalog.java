package com.sks.sksiskur.sicil;

import java.util.List;
import java.util.Optional;

public interface SicilUnitCatalog {
    List<WorkUnit> workUnits();

    List<Faculty> faculties();

    Optional<WorkUnit> authenticate(String birimKodu, String sifre);
}
