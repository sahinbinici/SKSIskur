import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, ApiError, authenticatedBlobUrl } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { PageHeader } from "../components/PageHeader";
import { Field, Shell, StatusBadge, formatDate, maskTc } from "../components/ui";
import type { Basvuru, Belge } from "../types";

function ocrSummary(belgeler: Belge[]) {
  const reviewed = belgeler.filter((b) => b.dogrulamaDurumu != null);
  const verified = reviewed.filter((b) => b.dogrulamaDurumu === "DOGRULANDI").length;
  const needsReview = reviewed.filter((b) => b.dogrulamaDurumu === "INCELEME_GEREKLI").length;
  const pending = belgeler.filter((b) => b.dogrulamaDurumu == null).length;
  return { reviewed: reviewed.length, verified, needsReview, pending };
}

export function AdminDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const [basvuru, setBasvuru] = useState<Basvuru | null>(null);
  const [nextId, setNextId] = useState<number | null>(null);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [ocrBusy, setOcrBusy] = useState(false);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    setError("");
    api.adminGet(Number(id))
      .then(setBasvuru)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Başvuru yüklenemedi."))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    if (!basvuru || basvuru.status !== "SUBMITTED") {
      setNextId(null);
      return;
    }
    api.adminList("SUBMITTED", "", basvuru.basvuruDonemiId, false)
      .then((list) => {
        const index = list.findIndex((item) => item.id === basvuru.id);
        if (index >= 0 && index < list.length - 1) {
          setNextId(list[index + 1].id);
          return;
        }
        setNextId(list.find((item) => item.id !== basvuru.id)?.id ?? null);
      })
      .catch(() => setNextId(null));
  }, [basvuru]);

  const ocr = useMemo(() => (basvuru ? ocrSummary(basvuru.belgeler) : null), [basvuru]);

  async function refreshOcr() {
    if (!basvuru) return;
    setOcrBusy(true);
    setError("");
    try {
      const data = await api.adminRefreshOcr(basvuru.id);
      setBasvuru(data);
      setMessage("OCR kontrolleri yenilendi.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "OCR yenilenemedi.");
    } finally {
      setOcrBusy(false);
    }
  }

  async function approve() {
    if (!basvuru) return;
    const ok = await confirm({
      title: "Başvuruyu onayla",
      message: `${basvuru.student.adSoyad} adlı öğrencinin başvurusu onaylanacak. Devam edilsin mi?`,
      confirmLabel: "Onayla"
    });
    if (!ok) return;
    setBusy(true);
    setError("");
    try {
      const data = await api.approve(basvuru.id);
      setBasvuru(data);
      setMessage("Başvuru onaylandı.");
      if (nextId) navigate(`/admin/basvuru/${nextId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Onaylanamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function reject() {
    if (!basvuru) return;
    const ok = await confirm({
      title: "Başvuruyu reddet",
      message: "Red gerekçesi öğrenciye iletilecek. Devam edilsin mi?",
      confirmLabel: "Reddet",
      variant: "danger"
    });
    if (!ok) return;
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
    const ok = await confirm({
      title: "Başvuruyu iade et",
      message: "Öğrenci eksik evrakları tamamlayıp yeniden gönderebilecek. Devam edilsin mi?",
      confirmLabel: "İade et"
    });
    if (!ok) return;
    setBusy(true);
    setError("");
    try {
      const data = await api.returnApplication(basvuru.id, note);
      setBasvuru(data);
      setMessage("Başvuru eksik evrak notuyla öğrenciye iade edildi.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Başvuru iade edilemedi.");
    } finally {
      setBusy(false);
    }
  }

  const student = basvuru?.student;

  return (
    <Shell home="/admin">
      <Link to="/admin" className="back-link">← Başvuru listesi</Link>
      <PageHeader
        title={student?.adSoyad ?? "Başvuru detayı"}
        description={basvuru ? `${student?.ogrenciNo ?? ""} · ${basvuru.basvuruDonemiAdi ?? "Dönem"}` : undefined}
        actions={basvuru && <StatusBadge status={basvuru.status} />}
      />
      {loading && <div className="alert alert-wait">Başvuru yükleniyor; belge OCR kontrolleri çalıştırılıyor…</div>}
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
            <Field label="İŞKUR nihai liste" value={basvuru ? (basvuru.kesinListede === true ? "Nihai listede" : basvuru.kesinListede === false ? "Nihai listede yok" : basvuru.status === "APPROVED" ? "İŞKUR nihai listesi bekleniyor" : "—") : undefined} />
            <Field label="Program" value={student?.program} />
            <Field label="Sınıf" value={student?.sinif} />
            <Field label="E-posta (başvuru)" value={basvuru?.iletisimEposta ?? student?.eposta} />
            <Field label="Cep telefonu (başvuru)" value={basvuru?.iletisimGsm ?? student?.gsm} />
            <Field label="IBAN" value={basvuru?.iban} />
            <Field label="Banka şube kodu" value={basvuru?.bankaSubeKodu} />
            <Field label="Hesap numarası" value={basvuru?.hesapNumarasi} />
            <Field label="Hesap sahibi" value={basvuru?.hesapSahibi} />
            <Field label="Gönderim" value={formatDate(basvuru?.gonderimTarihi)} />
            <Field label="İnceleme sorumlusu" value={basvuru?.atananAdmin} />
            <Field label="Kararı veren yönetici" value={basvuru?.inceleyenAdmin} />
          </div>
        </section>
        <section className="card" style={{ padding: 22 }}>
          <div className="section-row">
            <h3 className="section" style={{ margin: 0 }}>Belgeler</h3>
            {basvuru && basvuru.belgeler.length > 0 && (
              <button type="button" className="btn btn-secondary btn-compact" disabled={ocrBusy || loading} onClick={() => void refreshOcr()}>
                {ocrBusy ? "OCR çalışıyor…" : "OCR'yi yenile"}
              </button>
            )}
          </div>

          {ocr && ocr.reviewed > 0 && (
            <div className={`ocr-summary ${ocr.needsReview > 0 ? "ocr-summary-warn" : "ocr-summary-ok"}`}>
              {ocr.needsReview > 0 ? (
                <>
                  <strong>{ocr.needsReview} belgede manuel inceleme öneriliyor.</strong>
                  <span> Aşağıdaki ipuçlarını okuyup belgeleri açarak doğrulayın.</span>
                </>
              ) : (
                <>
                  <strong>Tüm belgeler OCR ile uyumlu görünüyor.</strong>
                  <span> Yine de belgeleri açıp son kontrolü yapın.</span>
                </>
              )}
            </div>
          )}

          <div className="doc-list">
            {basvuru?.belgeler.map((belge) => (
              <div className="doc-item" key={belge.id}>
                <div className="doc-item-content">
                  <div className="doc-item-head">
                    <b>{belge.belgeAdi}</b>
                    {belge.dogrulamaDurumu === "DOGRULANDI" && (
                      <span className="ocr-badge ocr-badge-ok">OCR uyumlu</span>
                    )}
                    {belge.dogrulamaDurumu === "INCELEME_GEREKLI" && (
                      <span className="ocr-badge ocr-badge-warn">Manuel inceleme</span>
                    )}
                    {belge.dogrulamaDurumu == null && (
                      <span className="ocr-badge">OCR bekliyor</span>
                    )}
                  </div>
                  <div className="doc-item-meta">{belge.orijinalAd}</div>
                  {belge.dogrulamaNotu && (
                    <div className={`ocr-hint ${belge.dogrulamaDurumu === "DOGRULANDI" ? "ocr-hint-ok" : "ocr-hint-warn"}`}>
                      {belge.dogrulamaNotu}
                    </div>
                  )}
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
          <p style={{ color: "var(--muted)", marginTop: -8 }}>Bu başvuru {basvuru.atananAdmin || "henüz atanmış olmayan"} yöneticinin iş listesinde.</p>
          {ocr && ocr.needsReview > 0 && (
            <div className="alert alert-wait" style={{ marginBottom: 14 }}>
              OCR {ocr.needsReview} belgede uyarı verdi. Onaylamadan önce belge ipuçlarını kontrol edin.
            </div>
          )}
          <label>İnceleme notu</label>
          <textarea rows={4} value={note} onChange={(e) => setNote(e.target.value)} placeholder="İade için eksik evrakları, ret için gerekçeyi belirtin." />
          <div className="row">
            <button className="btn btn-ok" disabled={busy} onClick={() => void approve()}>Onayla</button>
            <button className="btn btn-secondary" disabled={busy || !note.trim()} onClick={() => void returnApplication()}>İade et</button>
            <button className="btn btn-danger" disabled={busy || !note.trim()} onClick={() => void reject()}>Reddet</button>
            {nextId && (
              <button className="btn btn-secondary" disabled={busy} onClick={() => navigate(`/admin/basvuru/${nextId}`)}>
                Sonraki incelemede
              </button>
            )}
          </div>
        </section>
      )}
    </Shell>
  );
}
