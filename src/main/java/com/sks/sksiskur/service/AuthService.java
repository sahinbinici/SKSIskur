package com.sks.sksiskur.service;

import com.sks.sksiskur.config.DemoAccounts;
import com.sks.sksiskur.domain.AdminUser;
import com.sks.sksiskur.domain.BirimKullanici;
import com.sks.sksiskur.domain.Role;
import com.sks.sksiskur.domain.Student;
import com.sks.sksiskur.exception.ApiException;
import com.sks.sksiskur.proliz.ProlizClient;
import com.sks.sksiskur.proliz.ProlizLoginResponse;
import com.sks.sksiskur.proliz.ProlizStudentDto;
import com.sks.sksiskur.repository.AdminUserRepository;
import com.sks.sksiskur.repository.BirimKullaniciRepository;
import com.sks.sksiskur.repository.StudentRepository;
import com.sks.sksiskur.security.JwtService;
import com.sks.sksiskur.sicil.SicilUnitCatalog;
import com.sks.sksiskur.sicil.WorkUnit;
import com.sks.sksiskur.web.dto.AdminLoginRequest;
import com.sks.sksiskur.web.dto.AuthResponse;
import com.sks.sksiskur.web.dto.DemoInfoResponse;
import com.sks.sksiskur.web.dto.StudentLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final ProlizClient prolizClient;
    private final StudentRepository studentRepository;
    private final AdminUserRepository adminUserRepository;
    private final BirimKullaniciRepository birimKullaniciRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SicilUnitCatalog sicilUnitCatalog;
    private final BasvuruDonemiService basvuruDonemiService;
    private final boolean demoEnabled;

    public AuthService(
            ProlizClient prolizClient,
            StudentRepository studentRepository,
            AdminUserRepository adminUserRepository,
            BirimKullaniciRepository birimKullaniciRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SicilUnitCatalog sicilUnitCatalog,
            BasvuruDonemiService basvuruDonemiService,
            @Value("${app.demo.enabled:false}") boolean demoEnabled
    ) {
        this.prolizClient = prolizClient;
        this.studentRepository = studentRepository;
        this.adminUserRepository = adminUserRepository;
        this.birimKullaniciRepository = birimKullaniciRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sicilUnitCatalog = sicilUnitCatalog;
        this.basvuruDonemiService = basvuruDonemiService;
        this.demoEnabled = demoEnabled;
    }

    @Transactional
    public AuthResponse studentLogin(StudentLoginRequest request, HttpServletRequest httpRequest) {
        basvuruDonemiService.assertStudentAccessOpen();
        String ogrenciNo = request.ogrenciNo().trim();
        Student local = studentRepository.findByOgrenciNo(ogrenciNo).orElse(null);
        if (demoEnabled && local != null && local.getDemoSifreHash() != null && !local.getDemoSifreHash().isBlank()) {
            if (!passwordEncoder.matches(request.sifre(), local.getDemoSifreHash())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Öğrenci numarası veya şifre hatalı");
            }
            String token = jwtService.generateToken(local.getOgrenciNo(), Role.STUDENT.name(), local.getId());
            return new AuthResponse(token, Role.STUDENT, local.getAdSoyad(), local.getOgrenciNo(), null, null);
        }

        ProlizLoginResponse login = prolizClient.checkPassword(ogrenciNo, request.sifre(), clientIp(httpRequest));
        if (!login.success()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    login.message() != null ? login.message() : "Öğrenci numarası veya şifre hatalı");
        }

        ProlizStudentDto remote = prolizClient.fetchStudent(ogrenciNo);
        Student student = studentRepository.findByOgrenciNo(ogrenciNo).orElseGet(Student::new);
        applyStudent(student, remote);
        student = studentRepository.save(student);

        String token = jwtService.generateToken(student.getOgrenciNo(), Role.STUDENT.name(), student.getId());
        return new AuthResponse(token, Role.STUDENT, student.getAdSoyad(), student.getOgrenciNo(), null, null);
    }

    public AuthResponse adminLogin(AdminLoginRequest request) {
        AdminUser admin = adminUserRepository.findByUsername(request.username().trim())
                .filter(AdminUser::isAktif)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Kullanıcı adı veya şifre hatalı"));
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Kullanıcı adı veya şifre hatalı");
        }
        String token = jwtService.generateToken(admin.getUsername(), Role.ADMIN.name(), admin.getId());
        return new AuthResponse(token, Role.ADMIN, admin.getAdSoyad(), null, null, null);
    }

    public AuthResponse unitLogin(AdminLoginRequest request) {
        String username = request.username().trim();
        BirimKullanici local = birimKullaniciRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (local != null) {
            if (!passwordEncoder.matches(request.password(), local.getPasswordHash())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Kullanıcı adı veya şifre hatalı");
            }
            if (!local.isAktif()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Bu birim hesabı pasif.");
            }
            String token = jwtService.generateToken(local.getUsername(), Role.BIRIM.name(), local.getId(), local.getBirimKodu());
            return new AuthResponse(token, Role.BIRIM, local.getAdSoyad(), null, local.getBirimKodu(), local.getBirimAdi());
        }
        WorkUnit unit = sicilUnitCatalog.authenticate(username, request.password())
                .or(() -> demoEnabled ? DemoAccounts.authenticate(username, request.password()) : java.util.Optional.empty())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Kullanıcı adı veya şifre hatalı"));
        Long uid;
        try {
            uid = Long.parseLong(unit.kod());
        } catch (NumberFormatException ignored) {
            uid = 0L;
        }
        String token = jwtService.generateToken(unit.kod(), Role.BIRIM.name(), uid, unit.kod());
        return new AuthResponse(token, Role.BIRIM, unit.displayName(), null, unit.kod(), unit.displayName());
    }

    private void applyStudent(Student student, ProlizStudentDto remote) {
        student.setOgrenciNo(remote.ogrenciNo());
        student.setTcKimlikNo(remote.tcKimlikNo());
        student.setAd(nullToEmpty(remote.ad()));
        student.setSoyad(nullToEmpty(remote.soyad()));
        student.setUyruk(remote.uyruk());
        student.setDogumYeri(remote.dogumYeri());
        student.setDogumTarihi(remote.dogumTarihi());
        student.setCinsiyet(remote.cinsiyet());
        student.setEgitimDerecesi(remote.egitimDerecesi());
        student.setKayitTarihi(remote.kayitTarihi());
        student.setOgrenimDurumu(remote.ogrenimDurumu());
        student.setFakulte(remote.fakulte());
        student.setBolum(remote.bolum());
        student.setProgram(remote.program());
        student.setSinif(remote.sinif());
        student.setDurumu(remote.durumu());
        student.setEposta(remote.eposta());
        student.setGsm(remote.gsm());
        student.setAdres(remote.adres());
        student.setIl(remote.il());
        student.setIlce(remote.ilce());
        student.setFotoUrl(remote.fotoUrl());
        student.setDanisman(joinDanisman(remote.danismanUnvan(), remote.danismanAd(), remote.danismanSoyad()));
    }

    private String joinDanisman(String unvan, String ad, String soyad) {
        String value = String.join(" ",
                unvan != null ? unvan : "",
                ad != null ? ad : "",
                soyad != null ? soyad : "").replaceAll("\\s+", " ").trim();
        return value.isBlank() ? null : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String ip = request.getRemoteAddr();
        return ip == null || ip.isBlank() ? "127.0.0.1" : ip;
    }

    public DemoInfoResponse demoInfo() {
        if (!demoEnabled) {
            return new DemoInfoResponse(false, null, null, null, null, List.of(), List.of());
        }
        return new DemoInfoResponse(
                true,
                "admin",
                "admin123",
                DemoAccounts.STUDENT_PASSWORD,
                DemoAccounts.UNIT_PASSWORD,
                List.of(
                        new DemoInfoResponse.Ogrenci("99010001", "Ahmet Yılmaz", "Taslak başvuru"),
                        new DemoInfoResponse.Ogrenci("99010002", "Ayşe Demir", "İncelemede"),
                        new DemoInfoResponse.Ogrenci("99010003", "Mehmet Kaya", "Evrak onaylı, kayıt bekliyor"),
                        new DemoInfoResponse.Ogrenci("99010004", "Fatma Şahin", "Kesin kayıt, SKS birimine atanmış"),
                        new DemoInfoResponse.Ogrenci("99010005", "Ali Çelik", "Yedek liste"),
                        new DemoInfoResponse.Ogrenci("99010006", "Elif Koç", "Reddedildi")
                ),
                DemoAccounts.units().stream()
                        .map(unit -> new DemoInfoResponse.Birim(unit.kod(), unit.displayName()))
                        .toList()
        );
    }
}
