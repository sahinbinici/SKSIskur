package com.sks.sksiskur.takip;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class WorkScheduleRules {

    private static final DateTimeFormatter WEEK_LABEL =
            DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("tr-TR"));

    private WorkScheduleRules() {
    }

    /** Days 29-31 are carried into the following month's four-week quota. */
    public static YearMonth quotaMonth(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        return date.getDayOfMonth() >= 29 ? month.plusMonths(1) : month;
    }

    public static int monthlyQuota(int daysPerWeek) {
        return daysPerWeek * 4;
    }

    public static List<String> validateMonthlyQuota(YearMonth quotaMonth, Set<LocalDate> selected, int daysPerWeek) {
        List<String> errors = new ArrayList<>();
        int quota = monthlyQuota(daysPerWeek);
        for (LocalDate date : selected) {
            if (!quotaMonth(date).equals(quotaMonth)) {
                errors.add(date + " bu ayın kotasına ait değil.");
            }
        }
        if (selected.size() > quota) {
            errors.add(quotaMonth + " ayı için en fazla " + quota + " EK-6 günü seçilebilir (şu an " + selected.size() + ").");
        } else if (selected.size() != quota) {
            errors.add(quotaMonth + " ayı için 4 hafta × " + daysPerWeek + " gün, toplam " + quota
                    + " EK-6 günü seçilmelidir (şu an " + selected.size() + ").");
        }
        return errors;
    }

    public static boolean exceedsMonthlyQuota(YearMonth quotaMonth, Set<LocalDate> selected, int daysPerWeek) {
        return validateMonthlyQuota(quotaMonth, selected, daysPerWeek).stream()
                .anyMatch(message -> message.contains("en fazla"));
    }

    public static LocalDate weekMonday(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    public static boolean isInCurrentWeek(LocalDate date, LocalDate today) {
        return weekMonday(date).equals(weekMonday(today));
    }

    public static List<String> validateEkuant(YearMonth month, Set<LocalDate> selected, int daysPerWeek) {
        List<String> errors = new ArrayList<>();
        for (LocalDate date : selected) {
            if (!YearMonth.from(date).equals(month)) {
                errors.add(date + " seçilen aya ait değil.");
            }
        }
        Map<LocalDate, List<LocalDate>> daysByWeek = daysInMonthByWeek(month);
        Map<LocalDate, Long> selectedByWeek = selected.stream()
                .filter(d -> month.equals(YearMonth.from(d)))
                .collect(Collectors.groupingBy(d -> d.with(DayOfWeek.MONDAY), Collectors.counting()));

        for (Map.Entry<LocalDate, List<LocalDate>> entry : daysByWeek.entrySet()) {
            int available = entry.getValue().size();
            int required = Math.min(daysPerWeek, available);
            int actual = selectedByWeek.getOrDefault(entry.getKey(), 0L).intValue();
            if (actual > required) {
                LocalDate start = entry.getKey();
                LocalDate end = start.plusDays(6);
                errors.add(start.format(WEEK_LABEL) + " - " + end.format(WEEK_LABEL)
                        + " haftasında en fazla " + required + " gün seçilebilir (şu an " + actual + ").");
            } else if (actual != required) {
                LocalDate start = entry.getKey();
                LocalDate end = start.plusDays(6);
                errors.add(start.format(WEEK_LABEL) + " - " + end.format(WEEK_LABEL)
                        + " haftasında " + required + " gün seçilmelidir (şu an " + actual + ").");
            }
        }
        return errors;
    }

    public static boolean exceedsWeekLimit(YearMonth month, Set<LocalDate> selected, int daysPerWeek) {
        return validateEkuant(month, selected, daysPerWeek).stream()
                .anyMatch(message -> message.contains("en fazla"));
    }

    public static List<List<LocalDate>> calendarWeeks(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        LocalDate monday = first.with(DayOfWeek.MONDAY);
        List<List<LocalDate>> weeks = new ArrayList<>();
        while (!monday.isAfter(last)) {
            List<LocalDate> week = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                LocalDate day = monday.plusDays(i);
                week.add(YearMonth.from(day).equals(month) ? day : null);
            }
            weeks.add(week);
            monday = monday.plusWeeks(1);
        }
        return weeks;
    }

    public static Map<LocalDate, List<LocalDate>> daysInMonthByWeek(YearMonth month) {
        Map<LocalDate, List<LocalDate>> map = new TreeMap<>();
        LocalDate date = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        while (!date.isAfter(end)) {
            map.computeIfAbsent(date.with(DayOfWeek.MONDAY), key -> new ArrayList<>()).add(date);
            date = date.plusDays(1);
        }
        return map;
    }
}
