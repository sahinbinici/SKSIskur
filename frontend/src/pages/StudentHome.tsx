import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, ApiError } from "../api";
import { Field, Shell, StatusBadge, maskTc } from "../components/ui";
import type { Basvuru, StudentProfile } from "../types";

export function StudentHome() {
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [basvuru, setBasvuru] = useState<Basvuru | null>(null);
  const [error, setError] = useState("");
  const [photoFailed, setPhotoFailed] = useState(false);

  useEffect(() => {
    api.profile()
      .then(setProfile)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Öğrenci bilgileri yüklenemedi."));
    api.application()
      .then(setBasvuru)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Aktif başvuru dönemi bulunmuyor."));
  }, []);

  async function dismissAssignmentNotice() {
    if (!basvuru) return;
    try {
      setBasvuru(await api.markAtamaBildirimiOkundu());
    } catch {
      // ignore
    }
  }

  const yeniAtama = basvuru?.atananBirimAdi && !basvuru.atamaBildirimiOkundu;

  return (
    <Shell home="/panel">
      {error && <div className="alert alert-error">{error}</div>}
      {yeniAtama && (
        <div className="alert alert-ok">
          <strong>Birim atamanız yapıldı.</strong> {basvuru.atananBirimAdi} birimine atandınız.
          <button className="btn btn-secondary btn-compact" style={{ marginLeft: 12 }} onClick={dismissAssignmentNotice}>
            Tamam
          </button>
        </div>
      )}
      <div className="grid-2">
        <section className="card identity">
          {profile?.fotoUrl && !photoFailed ? (
            <img className="photo" src={profile.fotoUrl} alt={profile.adSoyad} onError={() => setPhotoFailed(true)} />
          ) : (
            <div className="photo fallback">{profile ? profile.ad.slice(0, 1) : "?"}</div>
          )}
          <div>
            <h3 className="section">{profile?.adSoyad ?? "Öğrenci bilgileri"}</h3>
            <div className="kv">
              <Field label="Öğrenci no" value={profile?.ogrenciNo} />
              <Field label="T.C. kimlik" value={maskTc(profile?.tcKimlikNo)} />
              <Field label="Fakülte / MYO" value={profile?.fakulte} />
              <Field label="Bölüm" value={profile?.bolum} />
              <Field label="Program" value={profile?.program} />
              <Field label="Sınıf" value={profile?.sinif} />
              <Field label="Öğrenim durumu" value={profile?.ogrenimDurumu} />
              <Field label="Kayıt tarihi" value={profile?.kayitTarihi} />
              <Field label="E-posta" value={profile?.eposta} />
              <Field label="Telefon" value={profile?.gsm} />
              <Field label="Adres" value={[profile?.adres, profile?.ilce, profile?.il].filter(Boolean).join(" / ")} />
              <Field label="Danışman" value={profile?.danisman} />
            </div>
          </div>
        </section>
        <aside className="card" style={{ padding: 24 }}>
          <h3 className="section">Başvuru durumu</h3>
          {basvuru && <StatusBadge status={basvuru.status} />}
          {!basvuru && !error && <div className="alert alert-wait">Aktif başvuru dönemi bekleniyor.</div>}
          {basvuru?.status === "APPROVED" && basvuru.kesinListede == null && (
            <div className="alert alert-wait">Evrakınız onaylandı. Başvurunuz İŞKUR incelemesi için gönderilecek; kesin kayıt listesi henüz açıklanmadı.</div>
          )}
          {basvuru?.status === "APPROVED" && basvuru.kesinListede === false && (
            <div className="alert alert-wait">Evrakınız onaylanmıştı ancak İŞKUR kesin kayıt listesinde yer almıyorsunuz.</div>
          )}
          {basvuru?.status === "RETURNED" && (
            <div className="alert alert-wait">Başvurunuz eksik evrak nedeniyle iade edildi. Yönetici notunu inceleyip eksiklerinizi tamamlayarak yeniden gönderin.</div>
          )}
          {basvuru?.kayitTuru === "KESIN" && !basvuru.atananBirimAdi && (
            <div className="alert alert-ok">İŞKUR kesin listesine alındınız. Birim ataması bekleniyor.</div>
          )}
          <p style={{ color: "var(--muted)", lineHeight: 1.6 }}>
            İkametgah, SGK dökümü, adli sicil kaydı, öğrenci belgesi ve kimlik belgesini yükleyip başvurunuzu gönderebilirsiniz.
            Başvuru incelemedeyken kilitlidir; iade edilirse evraklarınızı tamamlayıp yeniden gönderebilirsiniz.
          </p>
          {basvuru?.atananBirimAdi && !yeniAtama && (
            <div className="alert alert-ok">Atandığı birim: {basvuru.atananBirimAdi}</div>
          )}
          {basvuru?.adminNotu && (
            <div className="alert alert-wait">Yönetici notu: {basvuru.adminNotu}</div>
          )}
          <Link to="/basvuru" className="btn btn-gold" style={{ display: "inline-block", textDecoration: "none" }}>
            Başvuru sayfasına git
          </Link>
        </aside>
      </div>
    </Shell>
  );
}
