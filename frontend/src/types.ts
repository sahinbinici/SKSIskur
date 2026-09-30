export type Role = "STUDENT" | "ADMIN" | "BIRIM";
export type AdminRole = "SUPER_ADMIN" | "YONETICI";
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
  iskurListeYuklendi: boolean;
  iskurListeKayitSayisi: number;
  iskurListeYuklemeTarihi: string | null;
};

export type IskurListe = {
  donemId: number;
  donemAd: string;
  yuklendi: boolean;
  kayitSayisi: number;
  yuklemeTarihi: string | null;
  onizleme: { tcKimlikNo: string | null; ad: string; soyad: string; ogrenciNo: string | null }[];
};

export type IskurListeUpload = {
  donemId: number;
  kayitSayisi: number;
  atlananTekrar: number;
  yuklemeTarihi: string;
  yukleyenAdmin: string;
};

export type DocumentType = "IKAMETGAH" | "SGK_DOKUMU" | "ADLI_SICIL" | "OGRENCI_BELGESI" | "KIMLIK_BELGESI" | "HALKBANK_IBAN" | "HANE_SGK_DOKUMU";

export type AuthResponse = {
  token: string;
  role: Role;
  displayName: string;
  ogrenciNo: string | null;
  birimKodu: string | null;
  birimAdi: string | null;
  username: string | null;
  userId: number | null;
  adminRole: AdminRole | null;
};

export type DemoInfo = {
  enabled: boolean;
  adminUsername: string | null;
  adminPassword: string | null;
  ogrenciSifre: string | null;
  birimSifre: string | null;
  ogrenciSayisi: number;
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
  demoOgrenci: boolean;
  iskurBasvuruyaUygun: boolean;
  iskurBasvuruEngelMesaji: string | null;
};

export function canAccessApplication(profile: StudentProfile | null | undefined) {
  return Boolean(profile && (profile.demoOgrenci || profile.iskurBasvuruyaUygun));
}

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
  dogrulamaNotu: string | null;
  yuklemeTarihi: string;
};

export type Basvuru = {
  id: number;
  status: ApplicationStatus;
  iban: string | null;
  hesapSahibi: string | null;
  bankaSubeKodu: string | null;
  hesapNumarasi: string | null;
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
  kesinListede: boolean | null;
  atamaBildirimiOkundu: boolean;
  imzaBildirimiGonderildi: boolean;
  imzaBildirimiMesaji: string | null;
  imzaBildirimiGonderimTarihi: string | null;
  imzaBildirimiOkundu: boolean;
  imzaBildirimiEpostaGonderildi: boolean;
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
  rol: AdminRole;
  bekleyenBasvuruSayisi: number;
  olusturmaTarihi: string;
  benimHesabim: boolean;
};

export function isSuperAdmin(session: { role: Role; adminRole?: AdminRole | null } | null | undefined) {
  return session?.role === "ADMIN" && session.adminRole === "SUPER_ADMIN";
}

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
  imzaBildirimiGonderildi: boolean;
  yedek: number;
  birimler: DagitimBirim[];
  atanamayanlar: DagitimAtama[];
};

export type BelgeYuklemeFiltre = "TUMU" | "VAR" | "YOK";

export const BELGE_YUKLEME_FILTRE_LABEL: Record<BelgeYuklemeFiltre, string> = {
  TUMU: "Tümü",
  VAR: "Yüklenmiş",
  YOK: "Eksik"
};

export const ADMIN_BELGE_TIPLERI: { type: DocumentType; title: string }[] = [
  { type: "IKAMETGAH", title: "İkametgah belgesi" },
  { type: "SGK_DOKUMU", title: "SGK hizmet dökümü" },
  { type: "ADLI_SICIL", title: "Adli sicil kaydı" },
  { type: "OGRENCI_BELGESI", title: "Öğrenci belgesi" },
  { type: "KIMLIK_BELGESI", title: "T.C. kimlik kartı" },
  { type: "HANE_SGK_DOKUMU", title: "Aynı hanede yaşayanların SGK dökümü" }
];

export const DOCUMENT_TYPES: { type: DocumentType; title: string; hint: string }[] = [
  { type: "IKAMETGAH", title: "İkametgah belgesi", hint: "Kendi ikametgah belgenize ek olarak aynı hanede yaşayan kişilerin ikametgah belgesi eklenmelidir. e-Devlet barkodlu yerleşim yeri belgelerini ayrı ayrı yükleyin." },
  { type: "SGK_DOKUMU", title: "SGK hizmet dökümü / maaş bordrosu", hint: "Birden fazla e-Devlet SGK hizmet dökümü veya maaş bordrosu ekleyebilirsiniz. Barkodlu ve güncel belgeler yükleyin." },
  { type: "ADLI_SICIL", title: "Adli sicil kaydı", hint: "e-Devlet adli sicil kaydı. Barkodlu ve net çıktı yükleyin." },
  { type: "OGRENCI_BELGESI", title: "Öğrenci belgesi", hint: "Güncel, barkodlu öğrenci belgesi." },
  { type: "KIMLIK_BELGESI", title: "T.C. kimlik kartı", hint: "Ön ve arka yüzün aynı PDF'de veya tek görselde olduğu T.C. kimlik kartı." }
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

export function kesinListeDurumLabel(kesinListede: boolean | null, status?: ApplicationStatus) {
  if (kesinListede === true) return "Kesin listede";
  if (kesinListede === false) return "Kesin listede değil";
  if (status === "APPROVED") return "Karşılaştırma bekliyor";
  return "—";
}

export type PuantajDurum = "GELDI" | "GELMEDI" | "IZINLI" | "RAPORLU";
export type TakipStatus = "DRAFT" | "SUBMITTED";

export type OgrenciCalismaOzet = {
  basvuruId: number;
  birimAdi: string | null;
  birimKodu: string | null;
  donemAdi: string | null;
  yil: number;
  ay: number;
  kullanilanIzinGunu: number;
  izinGunLimiti: number;
  kalanIzinGunu: number;
  buAyTamGun: number;
  buAyEkuantGun: number;
  takipDurumu: TakipStatus | null;
  takipKilitli: boolean;
};

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

export type BirimDuyuru = {
  id: number;
  baslik: string;
  mesaj: string;
  gonderenAdmin: string;
  gonderimTarihi: string;
  tumBirimler: boolean;
  hedefSayisi: number;
  okunanSayisi: number;
  birimKodlari: string[];
};

export type BirimDuyuruInbox = {
  id: number;
  baslik: string;
  mesaj: string;
  gonderenAdmin: string;
  gonderimTarihi: string;
  okundu: boolean;
};

export type YoneticiPanosuDuyuru = {
  id: number;
  baslik: string;
  mesaj: string;
  aktif: boolean;
  gonderenAdmin: string;
  olusturmaTarihi: string;
  guncellemeTarihi: string;
};

export type KayitListeFiltre = "TUMU" | "KESIN_LISTEDE" | "KESIN_LISTEDE_DEGIL" | "ONAYLI_BASVURU";

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
  kesinListede: boolean | null;
  kayitTuru: KayitTuru | null;
  atananBirimAdi: string | null;
};

export type KesinListeUpload = {
  donemId: number;
  kayitSayisi: number;
  atlananTekrar: number;
  eslesen: number;
  kesinListedeDegil: number;
  listedeBasvuruEslesmedi: number;
  yuklemeTarihi: string;
  yukleyenAdmin: string;
};

export type ImzaBildirimiGonder = {
  hedefOgrenci: number;
  sayfaBildirimi: number;
  epostaGonderilen: number;
  epostaAtlanan: number;
  epostaBasarisiz: number;
  gonderimTarihi: string;
  gonderenAdmin: string;
};

export type KayitListesi = {
  kesinListeYuklendi: boolean;
  kesinListeYuklemeTarihi: string | null;
  kesinListeYukleyenAdmin: string | null;
  onayliBasvuru: number;
  kesinListede: number;
  kesinListedeDegil: number;
  kesinKarsilastirmaBekleyen: number;
  listedeBasvuruEslesmedi: number;
  kesinOnaylandi: boolean;
  onayTarihi: string | null;
  onaylayanAdmin: string | null;
  dagitimAcik: boolean;
  imzaBildirimiGonderildi: boolean;
  imzaBildirimiGonderimTarihi: string | null;
  imzaBildirimiGonderenAdmin: string | null;
  ogrenciler: KayitListesiSatir[];
  listedeEslesmeyenler: { tcKimlikNo: string | null; ad: string; soyad: string; ogrenciNo: string | null }[];
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

export type IslemTuru =
  | "GIRIS"
  | "BASVURU_TASLAK"
  | "BASVURU_GONDERIM"
  | "BASVURU_ONAY"
  | "BASVURU_RED"
  | "BASVURU_IADE"
  | "YONETICI_OLUSTUR"
  | "YONETICI_GUNCELLE"
  | "DONEM_AC"
  | "DONEM_KAPAT"
  | "ISKUR_LISTE_YUKLE"
  | "KESIN_LISTE_YUKLE"
  | "KESIN_LISTE_ONAY"
  | "KESIN_LISTE_GERI_AL"
  | "IMZA_BILDIRIMI"
  | "BIRIM_DUYURU_GONDER"
  | "YONETICI_PANO_DUYURU"
  | "OGRENCI_PANO_DUYURU"
  | "BIRIM_DAGITIM"
  | "BIRIM_ATAMA_DEGISTIR"
  | "BASVURU_YONETICI_DAGITIM"
  | "KAPALI_GUN_KAYDET"
  | "EKUANT_KAYDET"
  | "PUANTAJ_KAYDET"
  | "TAKIP_GONDER"
  | "ISKUR_PAKET_INDIR";

export type IslemLog = {
  id: number;
  zaman: string;
  rol: Role;
  tur: IslemTuru;
  kullaniciAdi: string;
  adSoyad: string | null;
  varlikTipi: string | null;
  varlikId: number | null;
  aciklama: string;
  detay: string | null;
};

export type IslemLogPage = {
  kayitlar: IslemLog[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};
