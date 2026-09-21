package com.sks.sksiskur.web.dto;

import java.time.LocalDate;
import java.util.List;

public record EkuantKaydetRequest(int yil, int ay, List<LocalDate> gunler) {
}
