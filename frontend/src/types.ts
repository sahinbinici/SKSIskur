export type Role = "STUDENT" | "ADMIN" | "BIRIM";
export type AgreementType = "KVKK" | "ISKUR_SOZLESMESI";

export type ApplicationStatus = "DRAFT" | "SUBMITTED" | "RETURNED" | "APPROVED" | "REJECTED";
export type KayitTuru = "KESIN" | "YEDEK";

export type BasvuruDonemi = {
  id: number;
  ad: string;
  aktif: boolean;
  olusturmaTarihi: string;
  kapanisTarihi: string | null;
  ogrenciBaslangicTarihi: string | null;
  ogrenciBitisTarihi: string | null;
  ogrenciGirisiAcik: boolean;
  aylikGelirLimiti: number;
};

export type DocumentType = "IKAMETGAH" | "SGK_DOKUMU" | "ADLI_SICIL" | "OGRENCI_BELGESI" | "KIMLIK_BELGESI" | "HALKBANK_IBAN" | "HANE_SGK_DOKUMU";

export type AuthResponse = {
  token: string;
  role: Role;
  displayName: string;
  ogrenciNo: string | null;
  birimKodu: string | null;
  birimAdi: string | null;
};

export type DemoInfo = {
  enabled: boolean;
  adminUsername: string | null;
  adminPassword: string | null;
  ogrenciSifre: string | null;
  birimSifre: string | null;
  ogrenciler: { ogrenciNo: string; adSoyad: string; durum: string }[];
  birimler: { kod: string; ad: string }[];
};

export type StudentProfile = {
  id: number;
  ogrenciNo: string;
  tcKimlikNo: string | null;
  ad: string;
  soyad: string;
  adSoyad: string;
  uyruk: string | null;
  dogumYeri: string | null;
  dogumTarihi: string | null;
  cinsiyet: string | null;
  egitimDerecesi: string | null;
  kayitTarihi: string | null;
  ogrenimDurumu: string | null;
  fakulte: string | null;
  bolum: string | null;
  program: string | null;
  sinif: string | null;
  durumu: string | null;
  eposta: string | null;
  gsm: string | null;
  adres: string | null;
  il: string | null;
  ilce: string | null;
  fotoUrl: string | null;
  danisman: string | null;
};

export type StudentAgreement = {
  tur: AgreementType;
  baslik: string;
  icerik: string;
  versiyon: number;
  kabulEdildi: boolean;
  kabulTarihi: string | null;
};

export type AgreementDocument = {
  tur: AgreementType;
  baslik: string;
  icerik: string;
  versiyon: number;
  guncellemeTarihi: string;
};

export type Belge = {
  id: number;
  belgeTipi: DocumentType;
  belgeAdi: string;
  haneUyesiAdi: string | null;
  orijinalAd: string;
  icerikTipi: string | null;
  boyutByte: number | null;
  dogrulamaDurumu: "DOGRULANDI" | "INCELEME_GEREKLI" | null;
  yuklemeTarihi: string;
};

export type Basvuru = {
  id: number;
  status: ApplicationStatus;
  iban: string | null;
  hesapSahibi: string | null;
  aylikGelir: number | null;
  adminNotu: string | null;
  inceleyenAdmin: string | null;
  atananAdmin: string | null;
  olusturmaTarihi: string;
  guncellemeTarihi: string;
  gonderimTarihi: string | null;
  incelemeTarihi: string | null;
  locked: boolean;
  kayitTuru: KayitTuru | null;
  kayitTarihi: string | null;
  atananBirimKodu: string | null;
  atananBirimAdi: string | null;
  atamaTuru: string | null;
  atamaTarihi: string | null;
  basvuruDonemiId: number | null;
  basvuruDonemiAdi: string | null;
  basvuruDonemiAktif: boolean;
  student: StudentProfile;
  belgeler: Belge[];
};

export type AdminUser = {
  id: number;
  username: string;
  adSoyad: string;
  aktif: boolean;
  bekleyenBasvuruSayisi: number;
  olusturmaTarihi: string;
};

export type BasvuruDagitim = {
  atananBasvuruSayisi: number;
  aktifYoneticiSayisi: number;
};

export type AdminOzet = {
  toplam: number;
  taslak: number;
  gonderildi: number;
  onaylandi: number;
  reddedildi: number;
  atanan: number;
  kesin: number;
  yedek: number;
  kayitBekleyen: number;
  kesinListeOnayli: boolean;
};

export type DagitimAtama = {
  basvuruId: number;
  ogrenciNo: string;
  adSoyad: string;
  fakulte: string | null;
  atamaTuru: string | null;
  birimAdi: string | null;
};

export type DagitimBirim = {
  kod: string;
  ad: string;
  kontenjan: number;
  dolu: number;
  kalan: number;
  ogrenciler: DagitimAtama[];
};

export type DagitimSonuc = {
  kontenjan: number;
  onaylanan: number;
  atanan: number;
  fakulteOncelikli: number;
  rastgele: number;
  atanamayan: number;
  atamaTarihi: string;
  kesinListeOnaylandi: boolean;
  yedek: number;
  birimler: DagitimBirim[];
  atanamayanlar: DagitimAtama[];
};

export const DOCUMENT_TYPES: { type: DocumentType; title: string; hint: string }[] = [
  { type: "IKAMETGAH", title: "İkametgah belgesi", hint: "e-Devlet barkodlu yerleşim yeri belgesi. Belge başlığı ve ad-soyad OCR ile kontrol edilir." },
  { type: "SGK_DOKUMU", title: "SGK hizmet dökümü / maaş bordrosu", hint: "Birden fazla e-Devlet SGK hizmet dökümü veya maaş bordrosu ekleyebilirsiniz. Her belge başlık, ad-soyad, işe giriş ve gelir için OCR ile kontrol edilir." },
  { type: "ADLI_SICIL", title: "Adli sicil kaydı", hint: "e-Devlet adli sicil kaydı. Belge başlığı ve ad-soyad OCR ile kontrol edilir." },
  { type: "OGRENCI_BELGESI", title: "Öğrenci belgesi", hint: "Güncel, barkodlu öğrenci belgesi. Belge başlığı ve ad-soyad OCR ile kontrol edilir." },
  { type: "KIMLIK_BELGESI", title: "T.C. kimlik kartı", hint: "Ön ve arka yüzün aynı PDF'de veya tek görselde olduğu T.C. kimlik kartı. Ad-soyad OCR ile kontrol edilir." }
];

export const STATUS_LABEL: Record<ApplicationStatus, string> = {
  DRAFT: "Taslak",
  SUBMITTED: "İncelemede",
  RETURNED: "Evrak tamamlaması bekleniyor",
  APPROVED: "Onaylandı",
  REJECTED: "Reddedildi"
};

export const KAYIT_LABEL: Record<KayitTuru, string> = {
  KESIN: "Kesin kayıt",
  YEDEK: "Yedek"
};

export type PuantajDurum = "GELDI" | "GELMEDI" | "IZINLI" | "RAPORLU";
export type TakipStatus = "DRAFT" | "SUBMITTED";

export type BirimOgrenci = {
  basvuruId: number;
  ogrenciNo: string;
  adSoyad: string;
  fakulte: string | null;
  bolum: string | null;
  program: string | null;
  eposta: string | null;
  gsm: string | null;
  kullanilanIzinGunu: number;
  izinGunLimiti: number;
  kalanIzinGunu: number;
};

export type PuantajGun = {
  tarih: string;
  durum: PuantajDurum | null;
  belgeAdi: string | null;
  belgeVar: boolean;
};

export type TakipDonem = {
  id: number | null;
  basvuruId: number;
  yil: number;
  ay: number;
  status: TakipStatus;
  locked: boolean;
  gonderimTarihi: string | null;
  ekuantGunler: string[];
  puantaj: PuantajGun[];
  ekuantUyarilari: string[];
  kapaliGunler: string[];
  ekuantKotaGunSayisi: number;
  ekuantKotaGunLimiti: number;
  toplamIzinGunu: number;
  izinGunLimiti: number;
  ogrenci: BirimOgrenci;
};

export const PUANTAJ_LABEL: Record<PuantajDurum, string> = {
  GELDI: "Geldi",
  GELMEDI: "Gelmedi",
  IZINLI: "İzinli",
  RAPORLU: "Raporlu"
};

export type BirimRaporOgrenci = {
  siraNo: number;
  basvuruId: number;
  ogrenciNo: string;
  tcKimlikNo: string | null;
  ad: string;
  soyad: string;
  iban: string | null;
  ekuantGunler: string[];
  geldiGunler: string[];
  toplamGun: number;
  toplamSaat: number;
};

export type BirimAylikRapor = {
  yil: number;
  ay: number;
  birimAdi: string;
  gunlukSaat: number;
  ogrenciler: BirimRaporOgrenci[];
};

export type IzinRaporOgrenci = {
  basvuruId: number;
  ogrenciNo: string;
  adSoyad: string;
  birimAdi: string | null;
  tarih: string;
  durum: PuantajDurum;
  belgeVar: boolean;
  belgeAdi: string | null;
};

export type WorkUnit = {
  kod: string;
  ad: string;
  kamuKod: number | null;
  kaynak: string | null;
};

export type DagitimBirimi = {
  id: number;
  kod: string;
  ad: string;
  kamuKod: number | null;
  kaynak: string;
  kontenjan: number;
  dagitimaAcik: boolean;
  ozel: boolean;
};

export type BirimKullanici = {
  id: number;
  username: string;
  adSoyad: string;
  birimKodu: string;
  birimAdi: string;
  aktif: boolean;
  olusturmaTarihi: string;
};

export type KayitListesiSatir = {
  basvuruId: number;
  ogrenciNo: string;
  tcKimlikNo: string | null;
  ad: string | null;
  soyad: string | null;
  adSoyad: string;
  fakulte: string | null;
  program: string | null;
  bolum: string | null;
  kayitTuru: KayitTuru | null;
  atananBirimAdi: string | null;
};

export type KayitListesi = {
  kesinOnaylandi: boolean;
  onayTarihi: string | null;
  onaylayanAdmin: string | null;
  bekleyen: number;
  kesin: number;
  yedek: number;
  dagitimAcik: boolean;
  ogrenciler: KayitListesiSatir[];
};

export type AdminTakipSatir = {
  basvuruId: number;
  ogrenciNo: string;
  adSoyad: string;
  birimKodu: string | null;
  birimAdi: string | null;
  ekuant: number;
  geldi: number;
  gelmedi: number;
  izinli: number;
  raporlu: number;
  status: TakipStatus | null;
  locked: boolean;
};

export type AdminTakipOzet = {
  yil: number;
  ay: number;
  birimKodu: string | null;
  birimAdi: string;
  ogrenciler: AdminTakipSatir[];
};
