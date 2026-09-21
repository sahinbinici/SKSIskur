package com.sks.sksiskur.proliz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProlizStudentListResponse(
        int gosterilenOgrenci,
        List<ProlizStudentDto> ogrenciler,
        int toplamOgrenci,
        String message
) {
}
