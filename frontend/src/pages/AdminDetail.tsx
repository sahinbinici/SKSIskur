import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, ApiError, authenticatedBlobUrl } from "../api";
import { Field, Shell, StatusBadge, formatDate, maskTc } from "../components/ui";
import type { Basvuru } from "../types";

export function AdminDetail() {
  const { id } = useParams();
  const [basvuru, setBasvuru] = useState<Basvuru | null>(null);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!id) return;
    api.adminGet(Number(id))
      .then(setBasvuru)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Başvuru yüklenemedi."));
  }, [id]);

  async function approve() {
    if (!basvuru) return;
    setBusy(true);
    setError("");
    try {
      const data = await api.approve(basvuru.id);
      setBasvuru(data);
      setMessage("Başvuru onaylandı.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Onaylanamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function reject() {
    if (!basvuru) return;
    setBusy(true);
    setError("");
    try {
      const data = await api.reject(basvuru.id, note);
      setBasvuru(data);
      setMessage("Başvuru reddedildi.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Reddedilemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function returnApplication() {
    if (!basvuru) return;
    setBusy(true);
    setError("");
    try {
      const data = await api.returnApplication(basvuru.id, note);
      setBasvuru(data);
      setMessage("Başvuru eksik evrak notuyla öğrenciye iade edildi. Öğrenci evraklarını tamamlayıp yeniden gönderebilir.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Başvuru iade edilemedi.");
    } finally {
      setBusy(false);
    }
  }

  const student = basvuru?.student;

  return (
    <Shell home="/admin">
      <Link to="/admin" style={{ color: "var(--muted)", textDecoration: "none" }}>← Listeye dön</Link>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "12px 0 18px" }}>
        <h3 className="section" style={{ margin: 0 }}>{student?.adSoyad ?? "Başvuru detayı"}</h3>
        {basvuru && <StatusBadge status={basvuru.status} />}
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {basvuru && !basvuru.basvuruDonemiAktif && (
        <div className="alert alert-wait">{basvuru.basvuruDonemiAdi || "Bu"} başvuru dönemi kapalıdır. Arşiv kaydı yalnız görüntülenebilir.</div>
      )}

      <div className="grid-2">
        <section className="card" style={{ padding: 22 }}>
          <h3 className="section">Öğrenci</h3>
          <div className="kv">
            <Field label="Öğrenci no" value={student?.ogrenciNo} />
            <Field label="T.C. kimlik" value={maskTc(student?.tcKimlikNo)} />
            <Field label="Doğum" value={[student?.dogumTarihi, student?.dogumYeri].filter(Boolean).join(" / ")} />
            <Field label="Fakülte" value={student?.fakulte} />
            <Field label="Atanan birim" value={basvuru?.atananBirimAdi} />
            <Field label="Kesin liste" value={basvuru ? (basvuru.kesinListede === true ? "Kesin listede" : basvuru.kesinListede === false ? "Kesin listede değil" : basvuru.status === "APPROVED" ? "Karşılaştırma bekliyor" : "—") : undefined} />
            <Field label="Program" value={student?.program} />
            <Field label="Sınıf" value={student?.sinif} />
            <Field label="İletişim" value={[student?.eposta, student?.gsm].filter(Boolean).join(" · ")} />
            <Field label="IBAN" value={basvuru?.iban} />
            <Field label="Hesap sahibi" value={basvuru?.hesapSahibi} />
            <Field label="Gönderim" value={formatDate(basvuru?.gonderimTarihi)} />
            <Field label="İnceleme sorumlusu" value={basvuru?.atananAdmin} />
            <Field label="Kararı veren yönetici" value={basvuru?.inceleyenAdmin} />
          </div>
        </section>
        <section className="card" style={{ padding: 22 }}>
          <h3 className="section">Belgeler</h3>
          <div className="doc-list">
            {basvuru?.belgeler.map((belge) => (
              <div className="doc-item" key={belge.id}>
                <div>
                  <b>{belge.belgeAdi}</b>
                  <div style={{ color: "var(--muted)", fontSize: 13 }}>{belge.orijinalAd}</div>
                  {belge.dogrulamaDurumu && <div style={{ color: belge.dogrulamaDurumu === "DOGRULANDI" ? "var(--ok)" : "var(--gold)", fontSize: 13 }}>
                    {belge.dogrulamaDurumu === "DOGRULANDI" ? "Ad-soyad OCR ile doğrulandı" : "OCR sonucu: yönetici incelemesi gerekli"}
                  </div>}
                </div>
                <button
                  className="btn btn-primary"
                  onClick={() => void authenticatedBlobUrl(api.adminDocumentUrl(basvuru.id, belge.id)).then((url) => window.open(url, "_blank"))}
                >
                  Aç
                </button>
              </div>
            ))}
            {basvuru && basvuru.belgeler.length === 0 && <p style={{ color: "var(--muted)" }}>Belge yok.</p>}
          </div>
        </section>
      </div>

      {basvuru?.status === "SUBMITTED" && basvuru.basvuruDonemiAktif && (
        <section className="card" style={{ padding: 22, marginTop: 18 }}>
          <h3 className="section">İnceleme</h3>
          <p style={{ color: "var(--muted)", marginTop: -8 }}>Bu başvuru {basvuru.atananAdmin || "henüz atanmış olmayan"} yöneticinin iş listesinde. Tüm aktif yöneticiler inceleme kararı verebilir.</p>
          <label>İnceleme notu</label>
          <textarea rows={4} value={note} onChange={(e) => setNote(e.target.value)} placeholder="İade için eksik evrakları, ret için gerekçeyi belirtin." />
          <div className="row">
            <button className="btn btn-ok" disabled={busy} onClick={approve}>Onayla</button>
            <button className="btn btn-secondary" disabled={busy || !note.trim()} onClick={returnApplication}>İade et</button>
            <button className="btn btn-danger" disabled={busy || !note.trim()} onClick={reject}>Reddet</button>
          </div>
        </section>
      )}
    </Shell>
  );
}
