import { useEffect, useState } from "react";
import { api, ApiError } from "../api";
import { Shell, formatDate } from "../components/ui";
import type { AgreementDocument, AgreementType } from "../types";

const agreementLabel: Record<AgreementType, string> = {
  KVKK: "KVKK aydınlatma metni",
  ISKUR_SOZLESMESI: "İŞKUR sözleşmesi"
};

export function AdminAgreementsPage() {
  const [documents, setDocuments] = useState<AgreementDocument[]>([]);
  const [selectedType, setSelectedType] = useState<AgreementType>("KVKK");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const selected = documents.find((document) => document.tur === selectedType);

  function choose(type: AgreementType) {
    const document = documents.find((item) => item.tur === type);
    setSelectedType(type);
    setTitle(document?.baslik ?? "");
    setContent(document?.icerik ?? "");
    setError("");
    setMessage("");
  }

  useEffect(() => {
    api.adminAgreements().then((items) => {
      setDocuments(items);
      const first = items.find((item) => item.tur === "KVKK") ?? items[0];
      if (first) {
        setSelectedType(first.tur);
        setTitle(first.baslik);
        setContent(first.icerik);
      }
    }).catch((err) => setError(err instanceof ApiError ? err.message : "Sözleşmeler yüklenemedi."));
  }, []);

  async function save() {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const updated = await api.updateAdminAgreement(selectedType, { baslik: title, icerik: content });
      setDocuments((items) => items.map((item) => item.tur === updated.tur ? updated : item));
      setTitle(updated.baslik);
      setContent(updated.icerik);
      setMessage(`Metin yayımlandı. Yeni sürüm: ${updated.versiyon}. Öğrenciler bu sürümü yeniden onaylayacaktır.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Sözleşme kaydedilemedi.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell home="/admin">
      <div className="page-heading">
        <div><h3 className="section">KVKK ve İŞKUR sözleşmeleri</h3><p className="muted">Buradan yayımlanan metin öğrenci başvuru ekranında sırasıyla gösterilir.</p></div>
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      <div className="agreement-admin-layout">
        <aside className="card agreement-admin-list">
          {(["KVKK", "ISKUR_SOZLESMESI"] as AgreementType[]).map((type) => {
            const document = documents.find((item) => item.tur === type);
            return <button type="button" key={type} className={selectedType === type ? "agreement-admin-item active" : "agreement-admin-item"} onClick={() => choose(type)}>
              <b>{agreementLabel[type]}</b>
              <span>{document ? `Sürüm ${document.versiyon}` : "Yükleniyor…"}</span>
            </button>;
          })}
        </aside>
        <section className="card agreement-admin-editor">
          <div className="agreement-editor-head"><div><h4>{agreementLabel[selectedType]}</h4><p className="muted">{selected ? `Son güncelleme: ${formatDate(selected.guncellemeTarihi)}` : ""}</p></div>{selected && <span className="agreement-version">Sürüm {selected.versiyon}</span>}</div>
          <label>Başlık</label>
          <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={180} disabled={busy} />
          <label>Metin</label>
          <textarea className="agreement-editor-textarea" value={content} onChange={(event) => setContent(event.target.value)} maxLength={50000} disabled={busy} />
          <div className="agreement-editor-footer"><span className="muted">Metin veya başlık değişirse sürüm artırılır ve öğrencilerden tekrar onay alınır.</span><button type="button" className="btn btn-gold" disabled={busy || !title.trim() || !content.trim()} onClick={save}>{busy ? "Kaydediliyor…" : "Yayımla"}</button></div>
        </section>
      </div>
    </Shell>
  );
}
