package com.sks.sksiskur.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IadeRequest(
        @NotBlank(message = "İade notu zorunludur")
        @Size(max = 2000, message = "Not en fazla 2000 karakter olabilir")
        String not
) {
}
