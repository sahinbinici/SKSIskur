package com.sks.sksiskur.service;

import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.web.dto.BasvuruDonemiCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class BasvuruDonemiServiceTest {

    @Autowired
    private BasvuruDonemiService service;

    @Test
    void closedPeriodStopsBeingActiveAndAllowsANewPeriod() {
        var active = service.requireActive();

        var closed = service.close(active.getId());

        assertFalse(closed.aktif());
        assertThrows(ApiException.class, service::requireActive);

        var next = service.create(new BasvuruDonemiCreateRequest("2026–2027 Bahar Dönemi", LocalDate.now(), LocalDate.now(), new java.math.BigDecimal("10000")));
        assertTrue(next.aktif());
        assertTrue(next.ogrenciGirisiAcik());
        assertEquals("2026–2027 Bahar Dönemi", service.requireActive().getAd());
    }

    @Test
    void studentAccessIsBlockedOutsideTheConfiguredDateRange() {
        service.close(service.requireActive().getId());
        service.create(new BasvuruDonemiCreateRequest(
                "Gelecek Dönem", LocalDate.now().plusDays(1), LocalDate.now().plusDays(10), new java.math.BigDecimal("10000")));

        assertThrows(ApiException.class, service::assertStudentAccessOpen);
    }
}
