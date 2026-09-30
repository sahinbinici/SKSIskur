import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, ApiError } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { PageHeader } from "../components/PageHeader";
import { Shell, formatDate } from "../components/ui";
import type { YoneticiPanosuDuyuru } from "../types";

const emptyForm = {
  baslik: "",
  mesaj: "",
  aktif: true
};

export function AdminPanoDuyurulariPanel() {
  const confirm = useConfirm();
  const [items, setItems] = useState<YoneticiPanosuDuyuru[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  function load() {
    return api.adminPanoDuyurulari().then(setItems);
  }

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Ana sayfa duyuruları alınamadı."));
  }, []);

  function resetForm() {
    setEditingId(null);
    setForm(emptyForm);
  }

  function startEdit(item: YoneticiPanosuDuyuru) {
    setEditingId(item.id);
    setForm({ baslik: item.baslik, mesaj: item.mesaj, aktif: item.aktif });
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!form.baslik.trim() || !form.mesaj.trim()) {
      setError("Başlık ve duyuru metni zorunludur.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const payload = {
        baslik: form.baslik.trim(),
        mesaj: form.mesaj.trim(),
        aktif: form.aktif
      };
      if (editingId) {
        await api.updatePanoDuyurusu(editingId, payload);
        setMessage("Ana sayfa duyurusu güncellendi.");
      } else {
        await api.createPanoDuyurusu(payload);
        setMessage("Ana sayfa duyurusu oluşturuldu.");
      }
      resetForm();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Duyuru kaydedilemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(item: YoneticiPanosuDuyuru) {
    const ok = await confirm({
      title: "Duyuruyu sil",
      message: `"${item.baslik}" ana sayfadan kaldırılacak. Devam edilsin mi?`,
      confirmLabel: "Sil",
      variant: "danger"
    });
    if (!ok) return;
    setBusy(true);
    setError("");
    try {
      await api.deletePanoDuyurusu(item.id);
      if (editingId === item.id) resetForm();
      setMessage("Duyuru silindi.");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Duyuru silinemedi.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}

      <form className="card" style={{ padding: 22, marginBottom: 18 }} onSubmit={(e) => void submit(e)}>
        <h3 className="section">{editingId ? "Duyuruyu düzenle" : "Yeni ana sayfa duyurusu"}</h3>
        <p style={{ color: "var(--muted)", marginTop: -8 }}>
          Aktif duyurular yönetici başvuru listesi sayfasında (<Link to="/admin">Başvurular</Link>) üst bölümde görünür.
        </p>
        <label>Başlık</label>
        <input
          value={form.baslik}
          onChange={(e) => setForm({ ...form, baslik: e.target.value })}
          placeholder="Örn. İnceleme süreci hatırlatması"
          maxLength={200}
        />
        <label>Duyuru metni</label>
        <textarea
          rows={5}
          value={form.mesaj}
          onChange={(e) => setForm({ ...form, mesaj: e.target.value })}
          placeholder="Tüm yöneticilerin göreceği bilgilendirme"
          maxLength={4000}
        />
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={form.aktif}
            onChange={(e) => setForm({ ...form, aktif: e.target.checked })}
          />
          Ana sayfada göster
        </label>
        <div className="row" style={{ marginTop: 16 }}>
          <button className="btn btn-primary" disabled={busy}>
            {busy ? "Kaydediliyor…" : editingId ? "Güncelle" : "Duyuru oluştur"}
          </button>
          {editingId && (
            <button type="button" className="btn btn-secondary" disabled={busy} onClick={resetForm}>
              İptal
            </button>
          )}
        </div>
      </form>

      <section className="card" style={{ padding: 22 }}>
        <h3 className="section">Kayıtlı duyurular</h3>
        <div style={{ overflow: "auto" }}>
          <table>
            <thead>
              <tr>
                <th>Tarih</th>
                <th>Başlık</th>
                <th>Durum</th>
                <th>Gönderen</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  <td>{formatDate(item.olusturmaTarihi)}</td>
                  <td>
                    <b>{item.baslik}</b>
                    <div style={{ color: "var(--muted)", fontSize: 13, maxWidth: 420, whiteSpace: "pre-wrap" }}>
                      {item.mesaj}
                    </div>
                  </td>
                  <td>{item.aktif ? "Aktif" : "Pasif"}</td>
                  <td>{item.gonderenAdmin}</td>
                  <td>
                    <div className="row">
                      <button type="button" className="btn btn-secondary btn-compact" disabled={busy} onClick={() => startEdit(item)}>
                        Düzenle
                      </button>
                      <button type="button" className="btn btn-danger btn-compact" disabled={busy} onClick={() => void remove(item)}>
                        Sil
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {items.length === 0 && (
                <tr>
                  <td colSpan={5} style={{ color: "var(--muted)" }}>Henüz ana sayfa duyurusu yok.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
