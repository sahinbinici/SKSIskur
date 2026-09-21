package com.sks.sksiskur.proliz;

import com.sks.sksiskur.exception.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ProlizClient {

    private final RestClient restClient;

    public ProlizClient(@Qualifier("prolizRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ProlizLoginResponse checkPassword(String ogrenciNo, String sifre, String ipAddress) {
        try {
            ProlizLoginResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/gau-ext/ogrenci/sifre-kontrol")
                            .queryParam("ogrenciNo", ogrenciNo)
                            .queryParam("sifre", sifre)
                            .queryParam("ipAddress", ipAddress)
                            .build())
                    .retrieve()
                    .body(ProlizLoginResponse.class);
            if (response == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Öğrenci bilgi sistemi yanıt vermedi.");
            }
            return response;
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Öğrenci bilgi sistemine şu anda ulaşılamıyor.");
        }
    }

    public ProlizStudentDto fetchStudent(String ogrenciNo) {
        try {
            ProlizStudentListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/gau-ext/ogrenci/aktif-liste")
                            .queryParam("ogrenciNo", ogrenciNo)
                            .queryParam("limit", 50)
                            .build())
                    .retrieve()
                    .body(ProlizStudentListResponse.class);
            if (response == null || response.ogrenciler() == null || response.ogrenciler().isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Öğrenci bilgisi bulunamadı.");
            }
            return response.ogrenciler().getFirst();
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Öğrenci bilgileri alınamadı. Lütfen tekrar deneyin.");
        }
    }
}
