import type { AdminOzet, AdminRole, AdminTakipOzet, AdminUser, AgreementDocument, AgreementType, ApplicationStatus, AuthResponse, Basvuru, BasvuruDagitim, BasvuruDalga, BasvuruDonemi, BelgeYuklemeFiltre, BirimAylikRapor, BirimDuyuru, BirimDuyuruInbox, BirimKullanici, BirimOgrenci, DagitimBirimi, DagitimSonuc, DemoInfo, DocumentType, ImzaBildirimiGonder, IskurListe, IskurListeUpload, IslemLogPage, IslemTuru, IzinRaporOgrenci, KayitListeFiltre, KayitListesi, KesinListeUpload, OgrenciCalismaOzet, PuantajDurum, Role, SozlesmeImzaBekleyen, StudentAgreement, StudentProfile, TakipDonem, WorkUnit, YoneticiPanosuDuyuru } from "./types";

const TOKEN_KEY = "sksiskur.token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = getToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    setToken(null);
    localStorage.removeItem("sksiskur.session");
  }
  if (!response.ok) {
    let message = "İşlem tamamlanamadı.";
    try {
      const data = await response.json();
      if (data?.message) {
        message = data.message;
      }
    } catch {
      // ignore parse errors
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    return response.json() as Promise<T>;
  }
  return undefined as T;
}

export const api = {
  studentLogin: (ogrenciNo: string, sifre: string) =>
    request<AuthResponse>("/api/auth/ogrenci", {
      method: "POST",
      body: JSON.stringify({ ogrenciNo, sifre })
    }),
  adminLogin: (username: string, password: string) =>
    request<AuthResponse>("/api/auth/admin", {
      method: "POST",
      body: JSON.stringify({ username, password })
    }),
  unitLogin: (username: string, password: string) =>
    request<AuthResponse>("/api/auth/birim", {
      method: "POST",
      body: JSON.stringify({ username, password })
    }),
  demoInfo: () => request<DemoInfo>("/api/auth/demo"),
  ogrenciPortalDuyurulari: () => request<YoneticiPanosuDuyuru[]>("/api/auth/ogrenci-duyurulari"),
  profile: () => request<StudentProfile>("/api/student/profil"),
  studentAgreements: () => request<StudentAgreement[]>("/api/student/sozlesmeler"),
  acceptStudentAgreement: (type: AgreementType) =>
    request<StudentAgreement[]>(`/api/student/sozlesmeler/${type}/kabul`, { method: "POST" }),
  application: () => request<Basvuru>("/api/student/basvuru"),
  studentCalismaOzet: async (): Promise<OgrenciCalismaOzet | null> => {
    const headers = new Headers();
    const token = getToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
    const response = await fetch("/api/student/calisma-ozeti", { headers });
    if (response.status === 204) {
      return null;
    }
    if (!response.ok) {
      let message = "Çalışma özeti alınamadı.";
      try {
        const data = await response.json();
        if (data?.message) message = data.message;
      } catch {
        // ignore
      }
      throw new ApiError(response.status, message);
    }
    return response.json() as Promise<OgrenciCalismaOzet>;
  },
  saveDraft: (payload: {
    iban: string;
    hesapSahibi: string;
    bankaSubeKodu: string;
    hesapNumarasi: string;
    eposta: string;
    gsm: string;
  }) =>
    request<Basvuru>("/api/student/basvuru", {
      method: "PUT",
      body: JSON.stringify(payload)
    }),
  submit: (payload: {
    iban: string;
    hesapSahibi: string;
    bankaSubeKodu: string;
    hesapNumarasi: string;
    eposta: string;
    gsm: string;
  }) =>
    request<Basvuru>("/api/student/basvuru/gonder", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  upload: async (belgeTipi: DocumentType, file: File, haneUyesiAdi?: string) => {
    const body = new FormData();
    body.append("belgeTipi", belgeTipi);
    body.append("file", file);
    if (haneUyesiAdi) body.append("haneUyesiAdi", haneUyesiAdi);
    return request<Basvuru>("/api/student/basvuru/belgeler", { method: "POST", body });
  },
  deleteDocument: (belgeId: number) =>
    request<Basvuru>(`/api/student/basvuru/belgeler/${belgeId}`, { method: "DELETE" }),
  studentDocumentUrl: (belgeId: number) => `/api/student/basvuru/belgeler/${belgeId}`,
  basvuruDonemleri: () => request<BasvuruDonemi[]>("/api/admin/basvuru-donemleri"),
  createBasvuruDonemi: (ad: string, ogrenciBaslangicTarihi: string, ogrenciBitisTarihi: string, aylikGelirLimiti: number) => request<BasvuruDonemi>("/api/admin/basvuru-donemleri", {
    method: "POST", body: JSON.stringify({ ad, ogrenciBaslangicTarihi, ogrenciBitisTarihi, aylikGelirLimiti })
  }),
  closeBasvuruDonemi: (id: number) => request<BasvuruDonemi>(`/api/admin/basvuru-donemleri/${id}/kapat`, { method: "POST" }),
  basvuruDalgalar: (donemId: number) => request<BasvuruDalga[]>(`/api/admin/basvuru-donemleri/${donemId}/dalgalar`),
  yeniBasvuruTuru: (donemId: number, ogrenciBaslangicTarihi: string, ogrenciBitisTarihi: string) =>
    request<BasvuruDalga>(`/api/admin/basvuru-donemleri/${donemId}/dalgalar/yeni-tur`, {
      method: "POST",
      body: JSON.stringify({ ogrenciBaslangicTarihi, ogrenciBitisTarihi })
    }),
  iskurListesiExcelUrl: (donemId: number) => `/api/admin/basvuru-donemleri/${donemId}/iskur-listesi.xlsx`,
  adminBasvuruQueryParams: (
    status?: ApplicationStatus | "",
    q?: string,
    donemId?: number,
    banaAtanan = false,
    belgeTipi?: DocumentType | "",
    belgeYukleme?: BelgeYuklemeFiltre,
    fakulte?: string
  ) => {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (q) params.set("q", q);
    if (donemId) params.set("donemId", String(donemId));
    if (banaAtanan) params.set("banaAtanan", "true");
    if (belgeTipi) params.set("belgeTipi", belgeTipi);
    if (belgeYukleme && belgeYukleme !== "TUMU") params.set("belgeYukleme", belgeYukleme);
    if (fakulte) params.set("fakulte", fakulte);
    return params;
  },
  adminBasvurularExcelUrl: (
    status?: ApplicationStatus | "",
    q?: string,
    donemId?: number,
    banaAtanan = false,
    belgeTipi?: DocumentType | "",
    belgeYukleme?: BelgeYuklemeFiltre,
    fakulte?: string
  ) => {
    const query = api.adminBasvuruQueryParams(status, q, donemId, banaAtanan, belgeTipi, belgeYukleme, fakulte).toString();
    return `/api/admin/basvurular.xlsx${query ? `?${query}` : ""}`;
  },
  adminBasvurularBelgeZipUrl: (
    status?: ApplicationStatus | "",
    q?: string,
    donemId?: number,
    banaAtanan = false,
    belgeTipi?: DocumentType | "",
    belgeYukleme?: BelgeYuklemeFiltre,
    fakulte?: string
  ) => {
    const query = api.adminBasvuruQueryParams(status, q, donemId, banaAtanan, belgeTipi, belgeYukleme, fakulte).toString();
    return `/api/admin/basvurular-belgeler.zip${query ? `?${query}` : ""}`;
  },
  adminBasvurularBelgeZipFilename: (belgeTipi: DocumentType) =>
    `basvurular-${belgeTipi.toLowerCase()}.zip`,
  updateBasvuruDonemiGelirLimiti: (id: number, limit: number) => request<BasvuruDonemi>(`/api/admin/basvuru-donemleri/${id}/gelir-limiti?limit=${encodeURIComponent(String(limit))}`, { method: "PUT" }),
  iskurListesi: (donemId: number, q?: string) =>
    request<IskurListe>(`/api/admin/basvuru-donemleri/${donemId}/iskur-listesi${q ? `?q=${encodeURIComponent(q)}` : ""}`),
  uploadIskurListesi: async (donemId: number, file: File) => {
    const body = new FormData();
    body.append("file", file);
    return request<IskurListeUpload>(`/api/admin/basvuru-donemleri/${donemId}/iskur-listesi`, { method: "POST", body });
  },
  adminAgreements: () => request<AgreementDocument[]>("/api/admin/sozlesmeler"),
  updateAdminAgreement: (type: AgreementType, payload: { baslik: string; icerik: string }) =>
    request<AgreementDocument>(`/api/admin/sozlesmeler/${type}`, { method: "PUT", body: JSON.stringify(payload) }),
  adminSummary: (donemId?: number) => request<AdminOzet>(`/api/admin/ozet${donemId ? `?donemId=${donemId}` : ""}`),
  adminList: (
    status?: ApplicationStatus | "",
    q?: string,
    donemId?: number,
    banaAtanan = false,
    belgeTipi?: DocumentType | "",
    belgeYukleme?: BelgeYuklemeFiltre,
    fakulte?: string
  ) => {
    const query = api.adminBasvuruQueryParams(status, q, donemId, banaAtanan, belgeTipi, belgeYukleme, fakulte).toString();
    return request<Basvuru[]>(`/api/admin/basvurular${query ? `?${query}` : ""}`);
  },
  adminFakulteler: (donemId?: number) =>
    request<string[]>(`/api/admin/basvurular/fakulteler${donemId ? `?donemId=${donemId}` : ""}`),
  adminGet: (id: number) => request<Basvuru>(`/api/admin/basvurular/${id}`),
  adminRefreshOcr: (id: number) => request<Basvuru>(`/api/admin/basvurular/${id}/ocr-kontrol`, { method: "POST" }),
  yoneticiler: () => request<AdminUser[]>("/api/admin/yoneticiler"),
  createYonetici: (payload: { username: string; password: string; adSoyad: string }) =>
    request<AdminUser>("/api/admin/yoneticiler", { method: "POST", body: JSON.stringify(payload) }),
  updateYonetici: (id: number, payload: { username: string; password?: string; adSoyad: string; aktif: boolean; rol?: AdminRole }) =>
    request<AdminUser>(`/api/admin/yoneticiler/${id}`, { method: "PUT", body: JSON.stringify(payload) }),
  basvurulariYoneticiyeDagit: (donemId?: number) =>
    request<BasvuruDagitim>(`/api/admin/yoneticiler/basvurulari-dagit${donemId ? `?donemId=${donemId}` : ""}`, { method: "POST" }),
  approve: (id: number) => request<Basvuru>(`/api/admin/basvurular/${id}/onayla`, { method: "POST" }),
  reject: (id: number, note: string) =>
    request<Basvuru>(`/api/admin/basvurular/${id}/reddet`, {
      method: "POST",
      body: JSON.stringify({ not: note })
    }),
  returnApplication: (id: number, note: string) =>
    request<Basvuru>(`/api/admin/basvurular/${id}/iade-et`, {
      method: "POST",
      body: JSON.stringify({ not: note })
    }),
  adminDocumentUrl: (basvuruId: number, belgeId: number) =>
    `/api/admin/basvurular/${basvuruId}/belgeler/${belgeId}`,
  dagitim: () => request<DagitimSonuc>("/api/admin/dagitim"),
  dagit: (yenidenDagit = false) =>
    request<DagitimSonuc>("/api/admin/dagitim", {
      method: "POST",
      body: JSON.stringify({ yenidenDagit })
    }),
  moveStudentUnit: (basvuruId: number, birimKodu: string) =>
    request<DagitimSonuc>(`/api/admin/dagitim/ogrenciler/${basvuruId}/birim`, {
      method: "PUT", body: JSON.stringify({ birimKodu })
    }),
  adminUnits: () => request<WorkUnit[]>("/api/admin/birimler"),
  dagitimBirimleri: () => request<DagitimBirimi[]>("/api/admin/dagitim-birimleri"),
  adminBirimDuyurulari: () => request<BirimDuyuru[]>("/api/admin/birim-duyurulari"),
  sendBirimDuyurusu: (payload: { baslik: string; mesaj: string; tumBirimler: boolean; birimKodlari: string[] }) =>
    request<{ id: number; hedefSayisi: number }>("/api/admin/birim-duyurulari", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  adminPanoDuyurulari: (aktif?: boolean) =>
    request<YoneticiPanosuDuyuru[]>(`/api/admin/pano-duyurulari${aktif ? "?aktif=true" : ""}`),
  createPanoDuyurusu: (payload: { baslik: string; mesaj: string; aktif: boolean }) =>
    request<YoneticiPanosuDuyuru>("/api/admin/pano-duyurulari", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  updatePanoDuyurusu: (id: number, payload: { baslik: string; mesaj: string; aktif: boolean }) =>
    request<YoneticiPanosuDuyuru>(`/api/admin/pano-duyurulari/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    }),
  deletePanoDuyurusu: (id: number) =>
    request<void>(`/api/admin/pano-duyurulari/${id}`, { method: "DELETE" }),
  adminOgrenciDuyurulari: (aktif?: boolean) =>
    request<YoneticiPanosuDuyuru[]>(`/api/admin/ogrenci-duyurulari${aktif ? "?aktif=true" : ""}`),
  createOgrenciDuyurusu: (payload: { baslik: string; mesaj: string; aktif: boolean }) =>
    request<YoneticiPanosuDuyuru>("/api/admin/ogrenci-duyurulari", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  updateOgrenciDuyurusu: (id: number, payload: { baslik: string; mesaj: string; aktif: boolean }) =>
    request<YoneticiPanosuDuyuru>(`/api/admin/ogrenci-duyurulari/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    }),
  deleteOgrenciDuyurusu: (id: number) =>
    request<void>(`/api/admin/ogrenci-duyurulari/${id}`, { method: "DELETE" }),
  createOzelDagitimBirimi: (payload: { kod: string; ad: string; kontenjan: number; dagitimaAcik: boolean }) =>
    request<DagitimBirimi>("/api/admin/dagitim-birimleri", { method: "POST", body: JSON.stringify(payload) }),
  updateDagitimBirimi: (id: number, payload: { kontenjan: number; dagitimaAcik: boolean }) =>
    request<DagitimBirimi>(`/api/admin/dagitim-birimleri/${id}`, { method: "PUT", body: JSON.stringify(payload) }),
  birimKullanicilar: () => request<BirimKullanici[]>("/api/admin/birim-kullanicilar"),
  createBirimKullanici: (payload: {
    username: string;
    password: string;
    adSoyad: string;
    birimKodu: string;
  }) =>
    request<BirimKullanici>("/api/admin/birim-kullanicilar", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  updateBirimKullanici: (id: number, payload: {
    username: string;
    password?: string;
    adSoyad: string;
    birimKodu: string;
    aktif: boolean;
  }) =>
    request<BirimKullanici>(`/api/admin/birim-kullanicilar/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    }),
  deleteBirimKullanici: (id: number) =>
    request<void>(`/api/admin/birim-kullanicilar/${id}`, { method: "DELETE" }),
  kayitListesi: (filtre?: KayitListeFiltre) =>
    request<KayitListesi>(`/api/admin/kayit-listesi${filtre ? `?filtre=${filtre}` : ""}`),
  kayitListesiExcelUrl: () => "/api/admin/kayit-listesi.xlsx",
  uploadKesinListe: async (file: File) => {
    const body = new FormData();
    body.append("file", file);
    return request<KesinListeUpload>("/api/admin/kayit-listesi/kesin-liste", { method: "POST", body });
  },
  markAtamaBildirimiOkundu: () =>
    request<Basvuru>("/api/student/basvuru/atama-bildirimi/okundu", { method: "POST" }),
  markImzaBildirimiOkundu: () =>
    request<Basvuru>("/api/student/basvuru/imza-bildirimi/okundu", { method: "POST" }),
  onaylaKesinListe: () => request<KayitListesi>("/api/admin/kayit-listesi/onayla", { method: "POST" }),
  gonderImzaBildirimi: () =>
    request<ImzaBildirimiGonder>("/api/admin/kayit-listesi/imza-bildirimi-gonder", { method: "POST" }),
  sozlesmeImzaBekleyen: (donemId?: number) =>
    request<SozlesmeImzaBekleyen[]>(
      `/api/admin/kayit-listesi/sozlesme-imza-bekleyen${donemId ? `?donemId=${donemId}` : ""}`
    ),
  sozlesmeImzalandi: (basvuruId: number) =>
    request<void>(`/api/admin/kayit-listesi/sozlesme-imza/${basvuruId}/imzalandi`, { method: "POST" }),
  sozlesmeImzaPasif: (basvuruIds: number[]) =>
    request<number>("/api/admin/kayit-listesi/sozlesme-imza/pasif", {
      method: "POST",
      body: JSON.stringify({ basvuruIds })
    }),
  geriAlKesinListe: () => request<KayitListesi>("/api/admin/kayit-listesi/geri-al", { method: "POST" }),
  adminTakip: (yil: number, ay: number, birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay) });
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return request<AdminTakipOzet>(`/api/admin/takip?${params}`);
  },
  adminKapaliGunler: (yil: number, ay: number, donemId?: number) =>
    request<string[]>(`/api/admin/takip/kapali-gunler?yil=${yil}&ay=${ay}${donemId ? `&donemId=${donemId}` : ""}`),
  saveAdminKapaliGunler: (yil: number, ay: number, gunler: string[], donemId?: number) =>
    request<string[]>(`/api/admin/takip/kapali-gunler?yil=${yil}&ay=${ay}${donemId ? `&donemId=${donemId}` : ""}`, {
      method: "PUT", body: JSON.stringify({ gunler })
    }),
  adminTakipRapor: (yil: number, ay: number, birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay) });
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return request<BirimAylikRapor>(`/api/admin/takip/rapor?${params}`);
  },
  adminKesilenOgrencilerUrl: (birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams();
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return `/api/admin/takip/iliskisi-kesilenler.csv?${params}`;
  },
  adminKesilenOgrencilerExcelUrl: (birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams();
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return `/api/admin/takip/iliskisi-kesilenler.xlsx?${params}`;
  },
  adminTakipExcelUrl: (yil: number, ay: number, birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay) });
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return `/api/admin/takip.xlsx?${params}`;
  },
  adminTakipRaporExcelUrl: (yil: number, ay: number, birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay) });
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return `/api/admin/takip/rapor.xlsx?${params}`;
  },
  adminIskurPaketiExcelUrl: (yil: number, ay: number, birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay) });
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return `/api/admin/takip/iskur-paketi.xlsx?${params}`;
  },
  adminIzinRaporlular: (yil: number, ay: number, birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay) });
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return request<IzinRaporOgrenci[]>(`/api/admin/takip/izin-rapor?${params}`);
  },
  adminIzinRaporCsvUrl: (yil: number, ay: number, birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay) });
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return `/api/admin/takip/izin-rapor.csv?${params}`;
  },
  adminIzinRaporExcelUrl: (yil: number, ay: number, birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay) });
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return `/api/admin/takip/izin-rapor.xlsx?${params}`;
  },
  adminIzinRaporBelgeleriZipUrl: (yil: number, ay: number, birimKodu?: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay) });
    if (birimKodu) params.set("birimKodu", birimKodu);
    if (donemId) params.set("donemId", String(donemId));
    return `/api/admin/takip/izin-rapor-belgeleri.zip?${params}`;
  },
  adminTakipOgrenci: (basvuruId: number, yil: number, ay: number, donemId?: number) =>
    request<TakipDonem>(`/api/admin/takip/ogrenciler/${basvuruId}?yil=${yil}&ay=${ay}${donemId ? `&donemId=${donemId}` : ""}`),
  adminTakipOnayla: (basvuruId: number, yil: number, ay: number, donemId?: number) =>
    request<TakipDonem>(`/api/admin/takip/ogrenciler/${basvuruId}/onayla?yil=${yil}&ay=${ay}${donemId ? `&donemId=${donemId}` : ""}`, {
      method: "POST"
    }),
  adminTakipOnaylaGonderilenler: (yil: number, ay: number, birimKodu: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay), birimKodu });
    if (donemId) params.set("donemId", String(donemId));
    return request<number>(`/api/admin/takip/onayla-gonderilenler?${params}`, { method: "POST" });
  },
  adminTakipIade: (basvuruId: number, yil: number, ay: number, donemId?: number) =>
    request<TakipDonem>(`/api/admin/takip/ogrenciler/${basvuruId}/iade?yil=${yil}&ay=${ay}${donemId ? `&donemId=${donemId}` : ""}`, {
      method: "POST"
    }),
  adminTakipIadeGonderilenler: (yil: number, ay: number, birimKodu: string, donemId?: number) => {
    const params = new URLSearchParams({ yil: String(yil), ay: String(ay), birimKodu });
    if (donemId) params.set("donemId", String(donemId));
    return request<number>(`/api/admin/takip/iade-gonderilenler?${params}`, { method: "POST" });
  },
  adminPuantajBelgeUrl: (basvuruId: number, yil: number, ay: number, tarih: string) =>
    `/api/admin/takip/ogrenciler/${basvuruId}/puantaj/belge?yil=${yil}&ay=${ay}&tarih=${tarih}`,
  birimOgrenciler: () => request<BirimOgrenci[]>("/api/birim/ogrenciler"),
  birimDuyurular: () => request<BirimDuyuruInbox[]>("/api/birim/duyurular"),
  markBirimDuyuruOkundu: (id: number) =>
    request<BirimDuyuruInbox>(`/api/birim/duyurular/${id}/okundu`, { method: "POST" }),
  birimTakip: (basvuruId: number, yil: number, ay: number) =>
    request<TakipDonem>(`/api/birim/ogrenciler/${basvuruId}/takip?yil=${yil}&ay=${ay}`),
  saveEkuant: (basvuruId: number, yil: number, ay: number, gunler: string[]) =>
    request<TakipDonem>(`/api/birim/ogrenciler/${basvuruId}/ekuant`, {
      method: "PUT",
      body: JSON.stringify({ yil, ay, gunler })
    }),
  savePuantaj: (basvuruId: number, yil: number, ay: number, kayitlar: { tarih: string; durum: PuantajDurum }[]) =>
    request<TakipDonem>(`/api/birim/ogrenciler/${basvuruId}/puantaj`, {
      method: "PUT",
      body: JSON.stringify({ yil, ay, kayitlar })
    }),
  uploadPuantajBelge: async (basvuruId: number, yil: number, ay: number, tarih: string, file: File) => {
    const body = new FormData();
    body.append("yil", String(yil));
    body.append("ay", String(ay));
    body.append("tarih", tarih);
    body.append("file", file);
    return request<TakipDonem>(`/api/birim/ogrenciler/${basvuruId}/puantaj/belge`, { method: "POST", body });
  },
  puantajBelgeUrl: (basvuruId: number, yil: number, ay: number, tarih: string) =>
    `/api/birim/ogrenciler/${basvuruId}/puantaj/belge?yil=${yil}&ay=${ay}&tarih=${tarih}`,
  gonderTakip: (basvuruId: number, yil: number, ay: number) =>
    request<TakipDonem>(`/api/birim/ogrenciler/${basvuruId}/gonder`, {
      method: "POST",
      body: JSON.stringify({ yil, ay })
    }),
  gonderBirimTakip: (yil: number, ay: number) =>
    request<{ yil: number; ay: number; gonderilenOgrenci: number }>("/api/birim/takip/gonder", {
      method: "POST",
      body: JSON.stringify({ yil, ay })
    }),
  birimRapor: (yil: number, ay: number) =>
    request<BirimAylikRapor>(`/api/birim/rapor?yil=${yil}&ay=${ay}`),
  birimRaporExcelUrl: (yil: number, ay: number) =>
    `/api/birim/rapor.xlsx?yil=${yil}&ay=${ay}`,
  islemLoglari: (params: {
    tur?: IslemTuru;
    rol?: Role;
    kullanici?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  } = {}) => {
    const query = new URLSearchParams();
    if (params.tur) query.set("tur", params.tur);
    if (params.rol) query.set("rol", params.rol);
    if (params.kullanici) query.set("kullanici", params.kullanici);
    if (params.from) query.set("from", params.from);
    if (params.to) query.set("to", params.to);
    if (params.page != null) query.set("page", String(params.page));
    if (params.size != null) query.set("size", String(params.size));
    const suffix = query.toString();
    return request<IslemLogPage>(`/api/admin/islem-loglari${suffix ? `?${suffix}` : ""}`);
  }
};

export { ApiError };

export async function authenticatedBlobUrl(path: string) {
  const token = getToken();
  const response = await fetch(path, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined
  });
  if (!response.ok) {
    const message = response.status === 504
      ? "Dosya oluşturma zaman aşımına uğradı. Birim filtresi seçerek tekrar deneyin veya birkaç dakika sonra yenileyin."
      : "Belge açılamadı.";
    throw new ApiError(response.status, message);
  }
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}

export async function downloadAuthenticatedFile(path: string, filename: string) {
  const url = await authenticatedBlobUrl(path);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
