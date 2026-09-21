import type { AdminOzet, AdminTakipOzet, AdminUser, AgreementDocument, AgreementType, ApplicationStatus, AuthResponse, Basvuru, BasvuruDagitim, BasvuruDonemi, BirimAylikRapor, BirimKullanici, BirimOgrenci, DagitimBirimi, DagitimSonuc, DemoInfo, DocumentType, IskurListe, IskurListeUpload, IzinRaporOgrenci, KayitListeFiltre, KayitListesi, KesinListeUpload, PuantajDurum, StudentAgreement, StudentProfile, TakipDonem, WorkUnit } from "./types";

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
  profile: () => request<StudentProfile>("/api/student/profil"),
  studentAgreements: () => request<StudentAgreement[]>("/api/student/sozlesmeler"),
  acceptStudentAgreement: (type: AgreementType) =>
    request<StudentAgreement[]>(`/api/student/sozlesmeler/${type}/kabul`, { method: "POST" }),
  application: () => request<Basvuru>("/api/student/basvuru"),
  saveDraft: (iban: string, hesapSahibi: string) =>
    request<Basvuru>("/api/student/basvuru", {
      method: "PUT",
      body: JSON.stringify({ iban, hesapSahibi })
    }),
  submit: (iban: string, hesapSahibi: string) =>
    request<Basvuru>("/api/student/basvuru/gonder", {
      method: "POST",
      body: JSON.stringify({ iban, hesapSahibi })
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
  iskurListesiExcelUrl: (donemId: number) => `/api/admin/basvuru-donemleri/${donemId}/iskur-listesi.xlsx`,
  adminBasvurularExcelUrl: (status?: ApplicationStatus | "", q?: string, donemId?: number, banaAtanan = false) => {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (q) params.set("q", q);
    if (donemId) params.set("donemId", String(donemId));
    if (banaAtanan) params.set("banaAtanan", "true");
    const query = params.toString();
    return `/api/admin/basvurular.xlsx${query ? `?${query}` : ""}`;
  },
  updateBasvuruDonemiGelirLimiti: (id: number, limit: number) => request<BasvuruDonemi>(`/api/admin/basvuru-donemleri/${id}/gelir-limiti?limit=${encodeURIComponent(String(limit))}`, { method: "PUT" }),
  iskurListesi: (donemId: number) => request<IskurListe>(`/api/admin/basvuru-donemleri/${donemId}/iskur-listesi`),
  uploadIskurListesi: async (donemId: number, file: File) => {
    const body = new FormData();
    body.append("file", file);
    return request<IskurListeUpload>(`/api/admin/basvuru-donemleri/${donemId}/iskur-listesi`, { method: "POST", body });
  },
  adminAgreements: () => request<AgreementDocument[]>("/api/admin/sozlesmeler"),
  updateAdminAgreement: (type: AgreementType, payload: { baslik: string; icerik: string }) =>
    request<AgreementDocument>(`/api/admin/sozlesmeler/${type}`, { method: "PUT", body: JSON.stringify(payload) }),
  adminSummary: (donemId?: number) => request<AdminOzet>(`/api/admin/ozet${donemId ? `?donemId=${donemId}` : ""}`),
  adminList: (status?: ApplicationStatus | "", q?: string, donemId?: number, banaAtanan = false) => {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (q) params.set("q", q);
    if (donemId) params.set("donemId", String(donemId));
    if (banaAtanan) params.set("banaAtanan", "true");
    const query = params.toString();
    return request<Basvuru[]>(`/api/admin/basvurular${query ? `?${query}` : ""}`);
  },
  adminGet: (id: number) => request<Basvuru>(`/api/admin/basvurular/${id}`),
  yoneticiler: () => request<AdminUser[]>("/api/admin/yoneticiler"),
  createYonetici: (payload: { username: string; password: string; adSoyad: string }) =>
    request<AdminUser>("/api/admin/yoneticiler", { method: "POST", body: JSON.stringify(payload) }),
  updateYonetici: (id: number, payload: { username: string; password?: string; adSoyad: string; aktif: boolean }) =>
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
  onaylaKesinListe: () => request<KayitListesi>("/api/admin/kayit-listesi/onayla", { method: "POST" }),
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
  adminPuantajBelgeUrl: (basvuruId: number, yil: number, ay: number, tarih: string) =>
    `/api/admin/takip/ogrenciler/${basvuruId}/puantaj/belge?yil=${yil}&ay=${ay}&tarih=${tarih}`,
  birimOgrenciler: () => request<BirimOgrenci[]>("/api/birim/ogrenciler"),
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
  birimRapor: (yil: number, ay: number) =>
    request<BirimAylikRapor>(`/api/birim/rapor?yil=${yil}&ay=${ay}`)
};

export { ApiError };

export async function authenticatedBlobUrl(path: string) {
  const token = getToken();
  const response = await fetch(path, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined
  });
  if (!response.ok) {
    throw new ApiError(response.status, "Belge açılamadı.");
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
