package com.sks.sksiskur.web.dto;

import java.time.LocalDate;
import java.util.List;

public record TakipKapaliGunKaydetRequest(List<LocalDate> gunler) {
}
