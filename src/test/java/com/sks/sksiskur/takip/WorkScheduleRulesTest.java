package com.sks.sksiskur.takip;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkScheduleRulesTest {

    @Test
    void september2026FullWeeksNeedThreeDays() {
        YearMonth month = YearMonth.of(2026, 9);
        Set<LocalDate> days = new HashSet<>();
        WorkScheduleRules.daysInMonthByWeek(month).forEach((week, weekDays) ->
                days.addAll(weekDays.subList(0, Math.min(3, weekDays.size()))));
        List<String> errors = WorkScheduleRules.validateEkuant(month, days, 3);
        assertTrue(errors.isEmpty(), () -> String.join(" | ", errors));
    }

    @Test
    void moreThanThreeDaysInAWeekIsRejected() {
        YearMonth month = YearMonth.of(2026, 9);
        Set<LocalDate> days = Set.of(
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 16),
                LocalDate.of(2026, 9, 17)
        );
        List<String> errors = WorkScheduleRules.validateEkuant(month, days, 3);
        assertTrue(errors.stream().anyMatch(message -> message.contains("en fazla")));
        assertTrue(WorkScheduleRules.exceedsWeekLimit(month, days, 3));
    }

    @Test
    void weekendCanBeSelected() {
        YearMonth month = YearMonth.of(2026, 9);
        Set<LocalDate> days = Set.of(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 16),
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 22),
                LocalDate.of(2026, 9, 23),
                LocalDate.of(2026, 9, 28),
                LocalDate.of(2026, 9, 29),
                LocalDate.of(2026, 9, 30));
        assertTrue(WorkScheduleRules.validateEkuant(month, days, 3).isEmpty());
    }

    @Test
    void isoWeekMondayGrouping() {
        LocalDate wednesday = LocalDate.of(2026, 9, 2);
        assertTrue(WorkScheduleRules.isInCurrentWeek(LocalDate.of(2026, 8, 31), wednesday));
        assertTrue(WorkScheduleRules.isInCurrentWeek(LocalDate.of(2026, 9, 6), wednesday));
        assertFalse(WorkScheduleRules.isInCurrentWeek(LocalDate.of(2026, 8, 30), wednesday));
        assertFalse(WorkScheduleRules.isInCurrentWeek(LocalDate.of(2026, 9, 7), wednesday));
    }

    @Test
    void daysFromTwentyNinthBelongToFollowingMonthsQuota() {
        assertEquals(YearMonth.of(2026, 9), WorkScheduleRules.quotaMonth(LocalDate.of(2026, 9, 28)));
        assertEquals(YearMonth.of(2026, 10), WorkScheduleRules.quotaMonth(LocalDate.of(2026, 9, 29)));
        assertEquals(12, WorkScheduleRules.monthlyQuota(3));
    }

    @Test
    void june2024HasFiveCalendarWeeks() {
        List<List<LocalDate>> weeks = WorkScheduleRules.calendarWeeks(YearMonth.of(2024, 6));
        assertEquals(5, weeks.size());
        assertEquals(LocalDate.of(2024, 6, 1), weeks.getFirst().get(5));
        assertEquals(LocalDate.of(2024, 6, 30), weeks.getLast().get(6));
    }
}
