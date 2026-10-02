import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, ApiError } from "../api";
import { Field, Shell, StatusBadge, maskTc } from "../components/ui";
import { StudentWorkPanel } from "../components/StudentWorkPanel";
import { canAccessApplication, type Basvuru, type OgrenciCalismaOzet, type StudentProfile } from "../types";

function studentApplicationAction(basvuru: Basvuru | null) {
  if (!basvuru) {
    return { label: "Başvuruya başla", hint: "Evraklarınızı yükleyip başvurunuzu gönderebilirsiniz." };
  }
  switch (basvuru.status) {
    case "DRAFT":
      return { label: "Başvuruyu tamamla ve gönder", hint: "Belgelerinizi yükleyip başvurunuzu gönderin." };
    case "SUBMITTED":
      return { label: "Başvuruyu görüntüle", hint: "Başvurunuz incelemede; değişiklik yapılamaz." };
    case "RETURNED":
      return { label: "Eksik evrakları tamamla", hint: "Yönetici notunu inceleyip eksikleri yükleyin." };
    case "APPROVED":
      return { label: "Başvuru durumunu görüntüle", hint: "Evrak onayınız alındı; kesin kayıt süreci devam ediyor." };
    case "REJECTED":
      return { label: "Başvuru sonucunu görüntüle", hint: "Başvurunuz reddedildi." };
    default:
      return { label: "Başvuru sayfasına git", hint: "" };
  }
}

export function StudentHome() {
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [basvuru, setBasvuru] = useState<Basvuru | null>(null);
  const [calismaOzet, setCalismaOzet] = useState<OgrenciCalismaOzet | null>(null);
  const [error, setError] = useState("");
  const [photoFailed, setPhotoFailed] = useState(false);

  useEffect(() => {
    api.profile()
      .then(async (data) => {
        setProfile(data);
        if (canAccessApplication(data)) {
          const application = await api.application();
          setBasvuru(application);
        }
        setCalismaOzet(await api.studentCalismaOzet());
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Öğrenci bilgileri yüklenemedi."));
  }, []);

  async function dismissAssignmentNotice() {
    if (!basvuru) return;
    try {
      setBasvuru(await api.markAtamaBildirimiOkundu());
    } catch {
      // ignore
    }
  }

  async function dismissImzaNotice() {
    if (!basvuru) return;
    try {
      setBasvuru(await api.markImzaBildirimiOkundu());
    } catch {
      // ignore
    }
  }

  const yeniAtama = basvuru?.atananBirimAdi && !basvuru.atamaBildirimiOkundu;
  const imzaBildirimi = basvuru?.imzaBildirimiGonderildi && !basvuru.imzaBildirimiOkundu && !basvuru.atananBirimAdi && !basvuru.sozlesmeImzalandi;
  const basvuruErisilebilir = canAccessApplication(profile);
  const action = studentApplicationAction(basvuru);

  return (
    <Shell home="/panel">
      {error && <div className="alert alert-error">{error}</div>}
      {imzaBildirimi && (
        <div className="alert alert-wait">
          <strong>Sözleşme imza daveti.</strong> {basvuru.imzaBildirimiMesaji}
          <button className="btn btn-secondary btn-compact" style={{ marginLeft: 12 }} onClick={dismissImzaNotice}>
            Okudum
          </button>
        </div>
      )}
      {yeniAtama && (
        <div className="alert alert-ok">
          <strong>Birim atamanız yapıldı.</strong> {basvuru.atananBirimAdi} birimine atandınız.
          <button className="btn btn-secondary btn-compact" style={{ marginLeft: 12 }} onClick={dismissAssignmentNotice}>
            Tamam
          </button>
        </div>
      )}
      {calismaOzet && <StudentWorkPanel ozet={calismaOzet} />}
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
              <p style={{ color: "var(--muted)", fontSize: 13, margin: "8px 0 0", gridColumn: "1 / -1" }}>
                Güncel iletişim bilgilerinizi başvuru ekranından girebilirsiniz; bilgilendirmeler bu adreslere gider.
              </p>
              <Field label="Adres" value={[profile?.adres, profile?.ilce, profile?.il].filter(Boolean).join(" / ")} />
              <Field label="Danışman" value={profile?.danisman} />
            </div>
          </div>
        </section>
        <aside className="card" style={{ padding: 24 }}>
          <h3 className="section">Başvuru durumu</h3>
          {!basvuruErisilebilir && profile && (
            <div className="alert alert-wait">
              {profile.iskurBasvuruEngelMesaji ?? "İŞKUR listesinde adınız olmadığı için başvuru yapamazsınız."}
            </div>
          )}
          {basvuruErisilebilir && basvuru && <StatusBadge status={basvuru.status} />}
          {basvuruErisilebilir && !basvuru && !error && <div className="alert alert-wait">Aktif başvuru dönemi bekleniyor.</div>}
          {basvuruErisilebilir && basvuru?.status === "APPROVED" && basvuru.kesinListede == null && (
            <div className="alert alert-wait">Evrakınız onaylandı. Başvurunuz İŞKUR incelemesine gönderilecek.</div>
          )}
          {basvuruErisilebilir && basvuru?.sozlesmeImzaPasif && (
            <div className="alert alert-wait">Sözleşme imzasına gelmediğiniz için kaydınız pasife alındı.</div>
          )}
          {basvuruErisilebilir && basvuru?.status === "APPROVED" && basvuru.kesinListede === false && !basvuru.sozlesmeImzaPasif && (
            <div className="alert alert-wait">Evrakınız onaylanmıştı ancak İŞKUR nihai listesinde yer almıyorsunuz.</div>
          )}
          {basvuruErisilebilir && basvuru?.status === "RETURNED" && (
            <div className="alert alert-wait">Başvurunuz eksik evrak nedeniyle iade edildi. Yönetici notunu inceleyip eksiklerinizi tamamlayarak yeniden gönderin.</div>
          )}
          {basvuruErisilebilir && basvuru?.kesinListede === true && !basvuru.sozlesmeImzaPasif && !basvuru.atananBirimAdi && !basvuru.imzaBildirimiGonderildi && (
            <div className="alert alert-ok">İŞKUR nihai listesine alındınız. Sözleşme imza daveti gönderilecek.</div>
          )}
          {basvuruErisilebilir && basvuru?.kesinListede === true && !basvuru.sozlesmeImzaPasif && !basvuru.atananBirimAdi && basvuru.imzaBildirimiGonderildi && !basvuru.sozlesmeImzalandi && basvuru.imzaBildirimiOkundu && (
            <div className="alert alert-wait">Sözleşme imzası için gelmeniz bekleniyor.</div>
          )}
          {basvuruErisilebilir && basvuru?.sozlesmeImzalandi && !basvuru.atananBirimAdi && (
            <div className="alert alert-ok">Sözleşme imzanız alındı. Birim ataması bekleniyor.</div>
          )}
          {basvuruErisilebilir && (
            <p style={{ color: "var(--muted)", lineHeight: 1.6 }}>
              {action.hint || "İkametgah, SGK dökümü, adli sicil kaydı, öğrenci belgesi ve kimlik belgesini yükleyip başvurunuzu gönderebilirsiniz."}
            </p>
          )}
          {basvuruErisilebilir && basvuru?.atananBirimAdi && !yeniAtama && (
            <div className="alert alert-ok">Atandığı birim: {basvuru.atananBirimAdi}</div>
          )}
          {basvuruErisilebilir && basvuru?.adminNotu && (
            <div className="alert alert-wait">Yönetici notu: {basvuru.adminNotu}</div>
          )}
          {basvuruErisilebilir && (
            <Link to="/basvuru" className="btn btn-gold" style={{ display: "inline-block", textDecoration: "none" }}>
              {action.label}
            </Link>
          )}
        </aside>
      </div>
    </Shell>
  );
}
