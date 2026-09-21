package com.sks.sksiskur.proliz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProlizLoginResponse(
        boolean success,
        String service,
        String ipAddress,
        String ogrenciNo,
        String message,
        String timestamp
) {
}
