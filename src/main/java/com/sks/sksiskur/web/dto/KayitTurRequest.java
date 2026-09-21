package com.sks.sksiskur.web.dto;

import com.sks.sksiskur.domain.KayitTuru;
import jakarta.validation.constraints.NotNull;

public record KayitTurRequest(@NotNull(message = "Kayıt türü zorunludur") KayitTuru tur) {
}
