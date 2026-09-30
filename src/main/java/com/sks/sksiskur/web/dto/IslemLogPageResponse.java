package com.sks.sksiskur.web.dto;

import java.util.List;

public record IslemLogPageResponse(
        List<IslemLogResponse> kayitlar,
        int page,
        int size,
        long total,
        int totalPages
) {
}
