package com.sks.sksiskur.web.dto;

public record DagitimBirimiResponse(
        Long id, String kod, String ad, Integer kamuKod, String kaynak,
        int kontenjan, boolean dagitimaAcik, boolean ozel
) { }
