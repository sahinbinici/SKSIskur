import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, ApiError, authenticatedBlobUrl } from "../api";
import { Shell, StatusBadge } from "../components/ui";
import { DOCUMENT_TYPES, canAccessApplication, type Basvuru, type DocumentType, type StudentAgreement, type StudentProfile } from "../types";

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

function extractHesapFromIban(iban: string) {
  const normalized = iban.replaceAll(/\s+/g, "").toUpperCase();
  if (normalized.length !== 26) return { bankaSubeKodu: "", hesapNumarasi: "" };
  const accountBlock = normalized.slice(14);
  return {
    bankaSubeKodu: normalized.slice(11, 15),
    hesapNumarasi: accountBlock.replace(/^0+/, "") || accountBlock
  };
}

function onlyDigits(value: string) {
  return value.replaceAll(/\D/g, "");
}

function isValidEmail(value: string) {
  const trimmed = value.trim();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed);
}

function isValidGsm(value: string) {
  const digits = onlyDigits(value);
  const normalized = digits.startsWith("90") && digits.length === 12
    ? digits.slice(2)
    : digits.length === 11 && digits.startsWith("0")
      ? digits.slice(1)
      : digits;
  return normalized.length === 10 && normalized.startsWith("5");
}

function focusIletisim() {
  window.setTimeout(() => {
    document.getElementById("iletisim-hesap")?.scrollIntoView({ behavior: "smooth", block: "start" });
    document.querySelector<HTMLInputElement>("#iletisim-hesap input[type='email']")?.focus();
  }, 0);
}

export function ApplicationPage() {
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [basvuru, setBasvuru] = useState<Basvuru | null>(null);
  const [iban, setIban] = useState("");
  const [bankaSubeKodu, setBankaSubeKodu] = useState("");
  const [hesapNumarasi, setHesapNumarasi] = useState("");
  const [hesapSahibi, setHesapSahibi] = useState("");
  const [eposta, setEposta] = useState("");
  const [gsm, setGsm] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [agreements, setAgreements] = useState<StudentAgreement[] | null>(null);
  const [agreementBusy, setAgreementBusy] = useState(false);

  async function refresh() {
    const data = await api.application();
    setBasvuru(data);
    setIban(data.iban ?? "");
    setBankaSubeKodu(data.bankaSubeKodu ?? "");
    setHesapNumarasi(data.hesapNumarasi ?? "");
    setHesapSahibi(data.hesapSahibi ?? data.student.adSoyad);
    setEposta(data.iletisimEposta ?? data.student.eposta ?? "");
    setGsm(data.iletisimGsm ?? data.student.gsm ?? "");
  }

  useEffect(() => {
    async function load() {
      const studentProfile = await api.profile();
      setProfile(studentProfile);
      if (!canAccessApplication(studentProfile)) {
        return;
      }
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
  const normalizedSubeKodu = onlyDigits(bankaSubeKodu);
  const normalizedHesapNumarasi = onlyDigits(hesapNumarasi);
  const iletisimHazir = Boolean(basvuru?.iletisimEposta && basvuru?.iletisimGsm);
  const hesapBilgisiHazir = Boolean(basvuru?.iban && basvuru?.bankaSubeKodu && basvuru?.hesapNumarasi);
  const pendingAgreement = agreements?.find((agreement) => !agreement.kabulEdildi);

  function updateIban(value: string) {
    setIban(value);
    const check = checkHalkbankIban(value);
    if (check.valid) {
      const extracted = extractHesapFromIban(check.normalized);
      setBankaSubeKodu(extracted.bankaSubeKodu);
      setHesapNumarasi(extracted.hesapNumarasi);
    }
  }

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
    if (!eposta.trim() && submit) {
      setError("E-posta adresi zorunludur. «İletişim bilgileri» alanlarını doldurun.");
      focusIletisim();
      return;
    }
    if (eposta.trim() && !isValidEmail(eposta)) {
      setError("Geçerli bir e-posta adresi giriniz.");
      focusIletisim();
      return;
    }
    if (!gsm.trim() && submit) {
      setError("Cep telefonu numarası zorunludur. «İletişim bilgileri» alanlarını doldurun.");
      focusIletisim();
      return;
    }
    if (gsm.trim() && !isValidGsm(gsm)) {
      setError("Geçerli bir cep telefonu giriniz (05xx xxx xx xx).");
      focusIletisim();
      return;
    }
    if (submit) {
      if (normalizedSubeKodu.length !== 4) {
        setError("Banka şube kodu 4 haneli olmalıdır.");
        return;
      }
      if (!normalizedHesapNumarasi) {
        setError("Hesap numarası zorunludur.");
        return;
      }
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const payload = {
        iban: ibanCheck.normalized || iban,
        hesapSahibi,
        bankaSubeKodu: normalizedSubeKodu,
        hesapNumarasi: normalizedHesapNumarasi,
        eposta: eposta.trim(),
        gsm: gsm.trim()
      };
      const data = submit ? await api.submit(payload) : await api.saveDraft(payload);
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

  if (profile && !canAccessApplication(profile)) {
    return (
      <Shell home="/panel">
        <h3 className="section">Başvuru evrakları</h3>
        <div className="card" style={{ padding: 24 }}>
          <div className="alert alert-wait">
            {profile.iskurBasvuruEngelMesaji ?? "İŞKUR listesinde adınız olmadığı için başvuru yapamazsınız."}
          </div>
          <Link to="/panel" className="btn btn-secondary" style={{ display: "inline-block", textDecoration: "none", marginTop: 12 }}>
            Panele dön
          </Link>
        </div>
      </Shell>
    );
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
      {basvuru?.imzaBildirimiGonderildi && !basvuru.imzaBildirimiOkundu && !basvuru.atananBirimAdi && !basvuru.sozlesmeImzalandi && (
        <div className="alert alert-wait">
          <strong>Sözleşme imza daveti.</strong> {basvuru.imzaBildirimiMesaji}
          <button
            type="button"
            className="btn btn-secondary btn-compact"
            style={{ marginLeft: 12 }}
            onClick={() => void api.markImzaBildirimiOkundu().then(setBasvuru).catch(() => undefined)}
          >
            Okudum
          </button>
        </div>
      )}
      {basvuru?.atananBirimAdi && !basvuru.atamaBildirimiOkundu && (
        <div className="alert alert-ok">
          <strong>Birim atamanız yapıldı.</strong> {basvuru.atananBirimAdi} birimine atandınız.
          <button
            type="button"
            className="btn btn-secondary btn-compact"
            style={{ marginLeft: 12 }}
            onClick={() => void api.markAtamaBildirimiOkundu().then(setBasvuru).catch(() => undefined)}
          >
            Tamam
          </button>
        </div>
      )}
      {basvuru?.sozlesmeImzaPasif && (
        <div className="alert alert-wait">Sözleşme imzasına gelmediğiniz için kaydınız pasife alındı. Başvuru değiştirilemez.</div>
      )}
      {basvuru?.status === "APPROVED" && basvuru.kesinListede === true && !basvuru.sozlesmeImzaPasif && !basvuru.atananBirimAdi && !basvuru.imzaBildirimiGonderildi && (
        <div className="alert alert-ok">İŞKUR nihai listesine alındınız. Sözleşme imzası için davet gönderilecek. Başvuru değiştirilemez.</div>
      )}
      {basvuru?.status === "APPROVED" && basvuru.kesinListede === true && !basvuru.sozlesmeImzaPasif && !basvuru.atananBirimAdi && basvuru.imzaBildirimiGonderildi && !basvuru.sozlesmeImzalandi && basvuru.imzaBildirimiOkundu && (
        <div className="alert alert-wait">Sözleşme imzası için gelmeniz bekleniyor. {basvuru.imzaBildirimiMesaji}</div>
      )}
      {basvuru?.sozlesmeImzalandi && !basvuru.atananBirimAdi && (
        <div className="alert alert-ok">Sözleşme imzanız alındı. Birim ataması bekleniyor. Başvuru değiştirilemez.</div>
      )}
      {basvuru?.status === "APPROVED" && basvuru.kesinListede === false && !basvuru.sozlesmeImzaPasif && (
        <div className="alert alert-wait">Evrakınız onaylanmıştı ancak İŞKUR nihai listesinde yer almıyorsunuz. Başvuru değiştirilemez.</div>
      )}
      {basvuru?.status === "APPROVED" && basvuru.kesinListede == null && !basvuru.sozlesmeImzaPasif && (
        <div className="alert alert-ok">Evrakınız onaylandı. Başvurunuz İŞKUR incelemesine gönderilecek. Başvuru değiştirilemez.</div>
      )}
      {basvuru?.atananBirimAdi && basvuru.atamaBildirimiOkundu && (
        <div className="alert alert-ok">Atandığı birim: {basvuru.atananBirimAdi}</div>
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
        <a href="#iletisim-hesap"><span>3</span><b>İletişim ve hesap</b><small>{iletisimHazir && hesapBilgisiHazir ? "Hazır" : "Bekliyor"}</small></a>
      </nav>

      <section id="belgeler" aria-labelledby="belgeler-baslik">
        <div className="section-intro"><h4 id="belgeler-baslik">Kişisel belgeler</h4><p>Her belgeyi ayrı yükleyin. Yükledikten sonra yönetici incelemesine gönderilir.</p></div>
      <div className="upload-grid">
        {DOCUMENT_TYPES.map((item) => {
          const documents = basvuru?.belgeler.filter((b) => b.belgeTipi === item.type) ?? [];
          const current = documents[0];
          if (item.type === "SGK_DOKUMU" || item.type === "IKAMETGAH") {
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
        <section id="iletisim-hesap" className="card" style={{ marginTop: 18, padding: 22 }}>
          <h3 className="section">İletişim bilgileri</h3>
          <p style={{ color: "var(--muted)", marginTop: -4, marginBottom: 14, lineHeight: 1.55 }}>
            OBS’deki kayıtlar güncel olmayabilir. Bilgilendirme e-postaları bu adreslere gönderilir; güncel e-posta ve cep telefonunuzu girin.
          </p>
          <label>E-posta</label>
          <input
            type="email"
            value={eposta}
            onChange={(e) => setEposta(e.target.value)}
            placeholder="ornek@mail.com"
            disabled={locked}
            autoComplete="email"
            maxLength={160}
          />
          <label>Cep telefonu</label>
          <input
            value={gsm}
            onChange={(e) => setGsm(e.target.value)}
            placeholder="05xx xxx xx xx"
            disabled={locked}
            inputMode="tel"
            autoComplete="tel"
            maxLength={20}
          />
          <h3 className="section" style={{ marginTop: 22 }}>Halkbank hesap bilgisi</h3>
          <label>IBAN</label>
          <input
            value={iban}
            onChange={(e) => updateIban(e.target.value.toUpperCase())}
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
          <label>Banka şube kodu</label>
          <input
            value={bankaSubeKodu}
            onChange={(e) => setBankaSubeKodu(onlyDigits(e.target.value).slice(0, 4))}
            placeholder="1338"
            disabled={locked}
            inputMode="numeric"
            maxLength={4}
          />
          <label>Hesap numarası</label>
          <input
            value={hesapNumarasi}
            onChange={(e) => setHesapNumarasi(onlyDigits(e.target.value).slice(0, 16))}
            placeholder="119577"
            disabled={locked}
            inputMode="numeric"
            maxLength={16}
          />
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
  locked,
  busy,
  onUpload,
  onDelete,
  onPreview
}: {
  title: string;
  hint: string;
  fileName?: string;
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
