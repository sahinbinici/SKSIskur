package com.sks.sksiskur.sicil;

import java.util.List;

public record Faculty(
        Integer fakKod,
        String ad,
        Integer kamuKod,
        List<String> aliases
) {
}
