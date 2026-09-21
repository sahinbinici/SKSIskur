package com.sks.sksiskur.sicil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class NameNormalizer {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Set<String> STOP = Set.of(
            "ve", "ile", "of", "the", "dekanlik", "dekanligi", "mudurluk", "mudurlugu",
            "dai", "bsk", "baskanligi", "baskanlik"
    );

    private NameNormalizer() {
    }

    public static String fold(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String t = value.toLowerCase(TR);
        t = t.replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
                .replace('ş', 's').replace('ö', 'o').replace('ç', 'c');
        t = t.replaceAll("meslek\\s*yuksekokulu", "myo");
        t = t.replaceAll("m\\.?\\s*y\\.?\\s*o\\.?", "myo");
        t = t.replaceAll("yuksekokulu", "yo");
        t = t.replaceAll("fakultesi", "fakulte");
        t = t.replaceAll("\\bfak\\b", "fakulte");
        t = t.replace("dekanligi", " ");
        t = t.replace("dekanlik", " ");
        t = t.replace("mudurlugu", " ");
        t = t.replace("mudurluk", " ");
        t = NON_ALNUM.matcher(t).replaceAll(" ");
        t = SPACES.matcher(t).replaceAll(" ").trim();
        List<String> tokens = Arrays.stream(t.split(" "))
                .filter(tok -> !tok.isBlank() && !STOP.contains(tok) && tok.length() > 1)
                .toList();
        return String.join(" ", tokens);
    }

    public static Set<String> tokens(String folded) {
        if (folded.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(Arrays.asList(folded.split(" ")));
    }

    public static double similarity(String left, String right) {
        String a = fold(left);
        String b = fold(right);
        if (a.isBlank() || b.isBlank()) {
            return 0;
        }
        if (a.equals(b)) {
            return 1;
        }
        if (a.contains(b) || b.contains(a)) {
            return 0.86;
        }
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0;
        }
        long overlap = ta.stream().filter(tb::contains).count();
        return (double) overlap / Math.max(ta.size(), tb.size());
    }
}
