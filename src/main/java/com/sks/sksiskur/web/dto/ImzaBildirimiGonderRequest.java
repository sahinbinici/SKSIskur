package com.sks.sksiskur.web.dto;

/**
 * Sözleşme imza daveti e-postası için admin tarafından özelleştirilebilen içerik.
 *
 * <p>Konu ve gövde şablonlarında aşağıdaki yer tutucular desteklenir:
 * <ul>
 *   <li>{@code {AD_SOYAD}} – Öğrencinin tam adı</li>
 *   <li>{@code {BIRIM}} – Öğrencinin sözleşme imzalayacağı birim adı</li>
 * </ul>
 * <p>Her iki alan da boş veya {@code null} geçilirse sistem varsayılan şablonu kullanır.
 */
public record ImzaBildirimiGonderRequest(
        String emailKonu,
        String emailSablon
) {
}
