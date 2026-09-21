package com.sks.sksiskur.sicil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class AssignmentPlanner {

    public record Candidate(Long basvuruId, String fakulte, String bolum) {
    }

    public record Placement(Long basvuruId, String birimKodu, String birimAdi, String tur) {
    }

    public record Plan(List<Placement> placements, List<Long> unassignedIds) {
    }

    public Plan plan(List<Candidate> students, List<WorkUnit> units, List<Faculty> faculties, int quota, Random random) {
        Map<String, Integer> quotas = new HashMap<>();
        units.forEach(unit -> quotas.put(unit.kod(), quota));
        return plan(students, units, faculties, quotas, random);
    }

    public Plan plan(List<Candidate> students, List<WorkUnit> units, List<Faculty> faculties,
                     Map<String, Integer> quotas, Random random) {
        FacultyUnitMatcher matcher = new FacultyUnitMatcher();
        List<WorkUnit> usable = units.stream()
                .filter(u -> u.kod() != null && !u.kod().isBlank())
                .filter(u -> u.displayName() != null && !u.displayName().isBlank())
                .toList();

        Map<String, Integer> remaining = new HashMap<>();
        Map<String, WorkUnit> byKod = new HashMap<>();
        for (WorkUnit unit : usable) {
            remaining.put(unit.kod(), Math.max(0, quotas.getOrDefault(unit.kod(), 0)));
            byKod.put(unit.kod(), unit);
        }

        Map<String, List<Candidate>> preferred = new LinkedHashMap<>();
        List<Candidate> overflow = new ArrayList<>();
        for (Candidate student : students) {
            Optional<WorkUnit> match = matcher.preferredUnit(student.fakulte(), student.bolum(), faculties, usable);
            if (match.isPresent() && remaining.containsKey(match.get().kod())) {
                preferred.computeIfAbsent(match.get().kod(), key -> new ArrayList<>()).add(student);
            } else {
                overflow.add(student);
            }
        }

        List<Placement> placements = new ArrayList<>();
        for (Map.Entry<String, List<Candidate>> entry : preferred.entrySet()) {
            List<Candidate> group = new ArrayList<>(entry.getValue());
            Collections.shuffle(group, random);
            int take = Math.min(group.size(), remaining.getOrDefault(entry.getKey(), 0));
            WorkUnit unit = byKod.get(entry.getKey());
            for (int i = 0; i < take; i++) {
                Candidate student = group.get(i);
                placements.add(new Placement(student.basvuruId(), unit.kod(), unit.displayName(), "FAKULTE"));
            }
            remaining.put(entry.getKey(), remaining.get(entry.getKey()) - take);
            overflow.addAll(group.subList(take, group.size()));
        }

        Collections.shuffle(overflow, random);
        List<WorkUnit> slots = new ArrayList<>();
        for (WorkUnit unit : usable) {
            int left = remaining.getOrDefault(unit.kod(), 0);
            for (int i = 0; i < left; i++) {
                slots.add(unit);
            }
        }
        Collections.shuffle(slots, random);

        List<Long> unassigned = new ArrayList<>();
        for (int i = 0; i < overflow.size(); i++) {
            Candidate student = overflow.get(i);
            if (i < slots.size()) {
                WorkUnit unit = slots.get(i);
                placements.add(new Placement(student.basvuruId(), unit.kod(), unit.displayName(), "RASTGELE"));
            } else {
                unassigned.add(student.basvuruId());
            }
        }
        return new Plan(placements, unassigned);
    }
}
