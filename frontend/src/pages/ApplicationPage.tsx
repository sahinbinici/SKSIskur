import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, ApiError, authenticatedBlobUrl } from "../api";
import { Shell, StatusBadge } from "../components/ui";
import { DOCUMENT_TYPES, type Basvuru, type DocumentType, type StudentAgreement } from "../types";

const HALKBANK_CODE = "00012";

function checkHalkbankIban(value: string) {
  const normalized = value.replaceAll(/\s+/g, "").toUpperCase();
  if (!normalized) return { normalized, message: "", valid: false, error: false };
  if (!normalized.startsWith("TR")) {
    return { normalized, message: "IBAN, TR ile başlamalıdır.", valid: false, error: true };
  }
  if (!/^TR\d*$/.test(normalized)) {
    return { normalized, message: "IBAN yalnızca TR ve rakamlardan oluşmalıdır.", valid: false, error: true };
  }
  if (normalized.length >= 9 && normalized.slice(4, 9) !== HALKBANK_CODE) {
    return { normalized, message: "Bu IBAN Halkbank'a ait görünmüyor.", valid: false, error: true };
  }
  if (normalized.length > 26) {
    return { normalized, message: "TR IBAN 26 karakter olmalıdır.", valid: false, error: true };
  }
  if (normalized.length === 26) {
    return { normalized, message: "Halkbank IBAN doğrulandı.", valid: true, error: false };
  }
  return { normalized, message: `Halkbank kodu doğrulanacak · ${normalized.length}/26 karakter`, valid: false, error: false };
}

export function ApplicationPage() {
  const [basvuru, setBasvuru] = useState<Basvuru | null>(null);
  const [iban, setIban] = useState("");
  const [hesapSahibi, setHesapSahibi] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [agreements, setAgreements] = useState<StudentAgreement[] | null>(null);
  const [agreementBusy, setAgreementBusy] = useState(false);

  async function refresh() {
    const data = await api.application();
    setBasvuru(data);
    setIban(data.iban ?? "");
    setHesapSahibi(data.hesapSahibi ?? data.student.adSoyad);
  }

  useEffect(() => {
    async function load() {
      const documents = await api.studentAgreements();
      setAgreements(documents);
      if (documents.every((document) => document.kabulEdildi)) {
        await refresh();
      }
    }
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Başvuru yüklenemedi."));
  }, []);

  const locked = Boolean(basvuru?.locked);
  const ibanCheck = checkHalkbankIban(iban);
  const pendingAgreement = agreements?.find((agreement) => !agreement.kabulEdildi);

  async function acceptAgreement() {
    if (!pendingAgreement) return;
    setAgreementBusy(true);
    setError("");
    try {
      const documents = await api.acceptStudentAgreement(pendingAgreement.tur);
      setAgreements(documents);
      if (documents.every((document) => document.kabulEdildi)) {
        await refresh();
        setMessage("KVKK ve İŞKUR sözleşmesi onayınız kaydedildi.");
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Sözleşme onayı kaydedilemedi.");
    } finally {
      setAgreementBusy(false);
    }
  }

  async function save(event: FormEvent, submit: boolean) {
    event.preventDefault();
    if (ibanCheck.error || (ibanCheck.normalized.length > 0 && !ibanCheck.valid)) {
      setError(ibanCheck.message || "Geçerli bir Halkbank IBAN giriniz.");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const data = submit
        ? await api.submit(iban, hesapSahibi)
        : await api.saveDraft(iban, hesapSahibi);
      setBasvuru(data);
      setMessage(submit ? "Başvurunuz inceleme için gönderildi." : "Taslak kaydedildi.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Kayıt tamamlanamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function onUpload(type: DocumentType, file: File) {
    setBusy(true);
    setError("");
    try {
      const data = await api.upload(type, file);
      setBasvuru(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Belge yüklenemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function onHouseholdUpload(haneUyesiAdi: string, file: File) {
    setBusy(true);
    setError("");
    try {
      const data = await api.upload("HANE_SGK_DOKUMU", file, haneUyesiAdi);
      setBasvuru(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Hane SGK dökümü yüklenemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(id: number) {
    setBusy(true);
    try {
      const data = await api.deleteDocument(id);
      setBasvuru(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Belge silinemedi.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell home="/panel">
      {pendingAgreement && <AgreementDialog agreement={pendingAgreement} busy={agreementBusy} onAccept={acceptAgreement} />}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <h3 className="section" style={{ margin: 0 }}>Başvuru evrakları</h3>
        {basvuru && <StatusBadge status={basvuru.status} />}
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {!agreements && !error && <div className="card" style={{ padding: 22 }}>Sözleşmeler yükleniyor…</div>}
      {basvuru?.status === "APPROVED" && (
        <div className="alert alert-ok">
          {basvuru.kayitTuru === "KESIN"
            ? "Kesin kayda alındınız. Başvuru değiştirilemez."
            : basvuru.kayitTuru === "YEDEK"
              ? "Yedek listeye alındınız. Başvuru değiştirilemez."
              : "Evrakınız onaylandı. Kesin kayıt listesi henüz kesinleşmedi."}
        </div>
      )}
      {basvuru?.status === "SUBMITTED" && (
        <div className="alert alert-wait">Başvurunuz incelemede. Sonuçlanana kadar değişiklik yapılamaz.</div>
      )}
      {basvuru?.status === "RETURNED" && (
        <div className="alert alert-wait">Başvurunuz eksik evrak için iade edildi. Yönetici notunu dikkate alarak evrakları tamamlayıp yeniden gönderin.</div>
      )}
      {basvuru?.adminNotu && (
        <div className="alert alert-wait">Red gerekçesi: {basvuru.adminNotu}</div>
      )}

      <nav className="application-steps" aria-label="Başvuru adımları">
        <a href="#belgeler"><span>1</span><b>Belgeler</b><small>{new Set(basvuru?.belgeler.filter((belge) => belge.belgeTipi !== "HANE_SGK_DOKUMU").map((belge) => belge.belgeTipi) ?? []).size}/5</small></a>
        <a href="#hane-sgk"><span>2</span><b>Hane SGK</b><small>{basvuru?.belgeler.some((belge) => belge.belgeTipi === "HANE_SGK_DOKUMU") ? "Tamam" : "Bekliyor"}</small></a>
        <a href="#hesap-bilgisi"><span>3</span><b>Hesap ve gönderim</b><small>{basvuru?.iban ? "Hazır" : "Bekliyor"}</small></a>
      </nav>

      <section id="belgeler" aria-labelledby="belgeler-baslik">
        <div className="section-intro"><h4 id="belgeler-baslik">Kişisel belgeler</h4><p>Her belgeyi ayrı yükleyin. OCR sonucu yönetici incelemesinde görünür.</p></div>
      <div className="upload-grid">
        {DOCUMENT_TYPES.map((item) => {
          const documents = basvuru?.belgeler.filter((b) => b.belgeTipi === item.type) ?? [];
          const current = documents[0];
          if (item.type === "SGK_DOKUMU") {
            return (
              <MultipleUploadSlot
                key={item.type}
                title={item.title}
                hint={item.hint}
                documents={documents}
                locked={locked}
                busy={busy}
                onUpload={(file) => onUpload(item.type, file)}
                onDelete={onDelete}
              />
            );
          }
          return (
            <UploadSlot
              key={item.type}
              title={item.title}
              hint={item.hint}
              fileName={current?.orijinalAd}
              verification={current?.dogrulamaDurumu}
              locked={locked}
              busy={busy}
              onUpload={(file) => onUpload(item.type, file)}
              onDelete={current ? () => onDelete(current.id) : undefined}
              onPreview={current ? () => void openDocument(api.studentDocumentUrl(current.id)) : undefined}
            />
          );
        })}
      </div>
      </section>

      <div id="hane-sgk">
      <HouseholdSgkDocuments
        documents={basvuru?.belgeler.filter((belge) => belge.belgeTipi === "HANE_SGK_DOKUMU") ?? []}
        locked={locked}
        busy={busy}
        onUpload={onHouseholdUpload}
        onDelete={onDelete}
      />
      </div>

      <form onSubmit={(e) => save(e, true)}>
        <section id="hesap-bilgisi" className="card" style={{ marginTop: 18, padding: 22 }}>
          <h3 className="section">Halkbank hesap bilgisi</h3>
          <label>IBAN</label>
          <input
            value={iban}
            onChange={(e) => setIban(e.target.value.toUpperCase())}
            placeholder="TR00 0001 2..."
            disabled={locked}
            inputMode="text"
            maxLength={32}
            aria-invalid={ibanCheck.error}
            className={ibanCheck.valid ? "input-valid" : ibanCheck.error ? "input-invalid" : ""}
          />
          {ibanCheck.message && (
            <p className={ibanCheck.error ? "iban-hint invalid" : ibanCheck.valid ? "iban-hint valid" : "iban-hint"}>
              {ibanCheck.message}
            </p>
          )}
          <label>Hesap sahibi</label>
          <input value={hesapSahibi} onChange={(e) => setHesapSahibi(e.target.value)} disabled={locked} />
          <div className="row">
            <button type="button" className="btn btn-primary" disabled={locked || busy || ibanCheck.error} onClick={(e) => save(e, false)}>
              Taslak kaydet
            </button>
            <button className="btn btn-gold" disabled={locked || busy || ibanCheck.error}>
              Başvuruyu gönder
            </button>
          </div>
        </section>
      </form>
    </Shell>
  );
}

function AgreementDialog({ agreement, busy, onAccept }: { agreement: StudentAgreement; busy: boolean; onAccept: () => void }) {
  const step = agreement.tur === "KVKK" ? "1 / 2" : "2 / 2";
  const previous = agreement.tur === "KVKK" ? "Önce KVKK aydınlatma metnini" : "KVKK metnini kabul ettiniz. Şimdi İŞKUR sözleşmesini";
  return (
    <div className="agreement-overlay" role="dialog" aria-modal="true" aria-labelledby="agreement-title">
      <section className="agreement-dialog">
        <div className="agreement-dialog-head">
          <div>
            <span className="agreement-step">Sözleşme adımı {step}</span>
            <h2 id="agreement-title">{agreement.baslik}</h2>
          </div>
          <span className="agreement-version">Sürüm {agreement.versiyon}</span>
        </div>
        <p className="muted">{previous} okuyup kabul etmeniz gerekir. Onay olmadan başvuru işlemlerine devam edemezsiniz.</p>
        <div className="agreement-content" tabIndex={0}>{agreement.icerik}</div>
        <div className="agreement-dialog-actions">
          <span className="muted">{agreement.tur === "KVKK" ? "Kabulden sonra İŞKUR sözleşmesi açılacaktır." : "Onaydan sonra başvuru ekranına geçilecektir."}</span>
          <button type="button" className="btn btn-gold" disabled={busy} onClick={onAccept}>
            {busy ? "Kaydediliyor…" : "Okudum, kabul ediyorum"}
          </button>
        </div>
      </section>
    </div>
  );
}

function HouseholdSgkDocuments({
  documents,
  locked,
  busy,
  onUpload,
  onDelete
}: {
  documents: Basvuru["belgeler"];
  locked: boolean;
  busy: boolean;
  onUpload: (haneUyesiAdi: string, file: File) => void;
  onDelete: (id: number) => void;
}) {
  const [haneUyesiAdi, setHaneUyesiAdi] = useState("");
  const [file, setFile] = useState<File | null>(null);

  function upload() {
    if (!haneUyesiAdi.trim() || !file) return;
    onUpload(haneUyesiAdi.trim(), file);
    setHaneUyesiAdi("");
    setFile(null);
  }

  return (
    <section className="card" style={{ marginTop: 18, padding: 22 }}>
      <h3 className="section">Aynı hanede yaşayanların SGK dökümleri</h3>
      <p className="muted">Kendi SGK hizmet dökümünüze ek olarak, aynı hanede yaşayan her kişi için belge ekleyiniz. Başvuru için en az bir hane üyesi dökümü zorunludur.</p>
      {!locked && (
        <div className="row" style={{ alignItems: "end", marginTop: 14 }}>
          <div style={{ flex: "1 1 220px" }}>
            <label>Hane üyesinin adı soyadı</label>
            <input value={haneUyesiAdi} onChange={(e) => setHaneUyesiAdi(e.target.value)} placeholder="Örn. Ayşe Yılmaz" disabled={busy} />
          </div>
          <label className="btn btn-primary" style={{ margin: 0 }}>
            {file ? file.name : "Dosya seç"}
            <input
              type="file"
              accept=".pdf,.jpg,.jpeg,.png"
              hidden
              disabled={busy}
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </label>
          <button type="button" className="btn btn-gold" disabled={busy || !haneUyesiAdi.trim() || !file} onClick={upload}>
            Ekle
          </button>
        </div>
      )}
      {documents.length === 0 ? (
        <div className="file-name" style={{ marginTop: 14 }}>Henüz hane üyesi SGK dökümü yüklenmedi</div>
      ) : (
        <div style={{ display: "grid", gap: 8, marginTop: 14 }}>
          {documents.map((document) => (
            <div className="file-name" key={document.id} style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
              <strong>{document.haneUyesiAdi}</strong>
              <span>{document.orijinalAd}</span>
              <button type="button" className="btn btn-ghost" onClick={() => void openDocument(api.studentDocumentUrl(document.id))}>Görüntüle</button>
              {!locked && <button type="button" className="btn btn-danger" disabled={busy} onClick={() => onDelete(document.id)}>Sil</button>}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function UploadSlot({
  title,
  hint,
  fileName,
  verification,
  locked,
  busy,
  onUpload,
  onDelete,
  onPreview
}: {
  title: string;
  hint: string;
  fileName?: string;
  verification?: "DOGRULANDI" | "INCELEME_GEREKLI" | null;
  locked: boolean;
  busy: boolean;
  onUpload: (file: File) => void;
  onDelete?: () => void;
  onPreview?: () => void;
}) {
  const inputId = useMemo(() => title.replaceAll(" ", "-").toLowerCase(), [title]);
  return (
    <div className="upload-slot card">
      <h4>{title}</h4>
      <p>{hint}</p>
      {fileName ? <div className="file-name">{fileName}</div> : <div className="file-name">Henüz yüklenmedi</div>}
      {verification === "DOGRULANDI" && <div className="upload-verification valid">Ad-soyad OCR ile doğrulandı.</div>}
      {verification === "INCELEME_GEREKLI" && <div className="upload-verification">OCR ad-soyadı net okuyamadı; yönetici inceleyecek.</div>}
      <div className="row" style={{ marginTop: 12 }}>
        {!locked && (
          <label className="btn btn-primary" style={{ margin: 0 }}>
            Dosya seç
            <input
              id={inputId}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png"
              hidden
              disabled={busy}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) onUpload(file);
                e.target.value = "";
              }}
            />
          </label>
        )}
        {onPreview && (
          <button type="button" className="btn btn-ghost" style={{ color: "var(--navy)", borderColor: "var(--line)" }} onClick={onPreview}>
            Görüntüle
          </button>
        )}
        {!locked && onDelete && (
          <button type="button" className="btn btn-danger" onClick={onDelete} disabled={busy}>Sil</button>
        )}
      </div>
    </div>
  );
}

function MultipleUploadSlot({
  title,
  hint,
  documents,
  locked,
  busy,
  onUpload,
  onDelete
}: {
  title: string;
  hint: string;
  documents: Basvuru["belgeler"];
  locked: boolean;
  busy: boolean;
  onUpload: (file: File) => void;
  onDelete: (id: number) => void;
}) {
  const inputId = useMemo(() => title.replaceAll(" ", "-").toLowerCase(), [title]);
  return (
    <div className="upload-slot card">
      <h4>{title}</h4>
      <p>{hint}</p>
      {documents.length === 0 ? <div className="file-name">Henüz yüklenmedi</div> : (
        <div className="document-list">
          {documents.map((document) => (
            <div className="document-item" key={document.id}>
              <span className="document-name" title={document.orijinalAd}>{document.orijinalAd}</span>
              {document.dogrulamaDurumu === "DOGRULANDI" && <span className="document-status valid">OCR doğrulandı</span>}
              {document.dogrulamaDurumu === "INCELEME_GEREKLI" && <span className="document-status">İnceleme gerekli</span>}
              <button type="button" className="btn btn-secondary btn-compact" onClick={() => void openDocument(api.studentDocumentUrl(document.id))}>Görüntüle</button>
              {!locked && <button type="button" className="btn btn-danger" disabled={busy} onClick={() => onDelete(document.id)}>Sil</button>}
            </div>
          ))}
        </div>
      )}
      {!locked && (
        <div className="row" style={{ marginTop: 12 }}>
          <label className="btn btn-primary" style={{ margin: 0 }}>
            {documents.length === 0 ? "Dosya seç" : "Yeni belge ekle"}
            <input id={inputId} type="file" accept=".pdf,.jpg,.jpeg,.png" hidden disabled={busy} onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) onUpload(file);
              e.target.value = "";
            }} />
          </label>
        </div>
      )}
    </div>
  );
}

async function openDocument(path: string) {
  const url = await authenticatedBlobUrl(path);
  window.open(url, "_blank", "noopener,noreferrer");
}
