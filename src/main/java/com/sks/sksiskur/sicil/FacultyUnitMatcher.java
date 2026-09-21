package com.sks.sksiskur.sicil;

import java.util.List;
import java.util.Optional;

public class FacultyUnitMatcher {

    private static final double MIN_SCORE = 0.45;

    public Optional<WorkUnit> preferredUnit(String fakulte, String bolum, List<Faculty> faculties, List<WorkUnit> units) {
        Faculty faculty = bestFaculty(fakulte, bolum, faculties).orElse(null);
        String query = firstNonBlank(fakulte, bolum);
        WorkUnit best = null;
        double bestScore = 0;

        for (WorkUnit unit : units) {
            double score = scoreUnit(query, faculty, unit);
            if (score > bestScore) {
                bestScore = score;
                best = unit;
            }
        }
        if (best == null || bestScore < MIN_SCORE) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private Optional<Faculty> bestFaculty(String fakulte, String bolum, List<Faculty> faculties) {
        Faculty best = null;
        double bestScore = 0;
        for (Faculty faculty : faculties) {
            double score = Math.max(scoreFaculty(fakulte, faculty), scoreFaculty(bolum, faculty) * 0.85);
            if (score > bestScore) {
                bestScore = score;
                best = faculty;
            }
        }
        if (best == null || bestScore < MIN_SCORE) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private double scoreFaculty(String name, Faculty faculty) {
        double best = NameNormalizer.similarity(name, faculty.ad());
        if (faculty.aliases() != null) {
            for (String alias : faculty.aliases()) {
                best = Math.max(best, NameNormalizer.similarity(name, alias));
            }
        }
        return best;
    }

    private double scoreUnit(String studentUnitName, Faculty faculty, WorkUnit unit) {
        double nameScore = NameNormalizer.similarity(studentUnitName, unit.displayName());
        nameScore = Math.max(nameScore, NameNormalizer.similarity(studentUnitName, unit.adUzun()));
        if (faculty != null) {
            nameScore = Math.max(nameScore, NameNormalizer.similarity(faculty.ad(), unit.displayName()));
            if (faculty.aliases() != null) {
                for (String alias : faculty.aliases()) {
                    nameScore = Math.max(nameScore, NameNormalizer.similarity(alias, unit.displayName()));
                }
            }
            if (faculty.kamuKod() != null && faculty.kamuKod().equals(unit.kamuKod()) && faculty.kamuKod() != 0) {
                nameScore = Math.max(nameScore, 0.72 + nameScore * 0.2);
            }
        }
        String folded = NameNormalizer.fold(unit.displayName());
        if (folded.contains("fakulte") || folded.contains("myo") || folded.contains("yo")
                || folded.contains("enstitu") || folded.contains("konserv")) {
            nameScore += 0.08;
        }
        return Math.min(nameScore, 1.0);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
