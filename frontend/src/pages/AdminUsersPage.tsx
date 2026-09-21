import { FormEvent, useEffect, useState } from "react";
import { api, ApiError } from "../api";
import { Shell, formatDate } from "../components/ui";
import type { AdminUser } from "../types";

const emptyForm = { username: "", password: "", adSoyad: "", aktif: true };

export function AdminUsersPage() {
  const [items, setItems] = useState<AdminUser[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const load = () => api.yoneticiler().then(setItems);

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Yönetici hesapları alınamadı."));
  }, []);

  function reset() {
    setEditingId(null);
    setForm(emptyForm);
  }

  function edit(item: AdminUser) {
    setEditingId(item.id);
    setForm({ username: item.username, adSoyad: item.adSoyad, password: "", aktif: item.aktif });
    setError("");
    setMessage("");
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      if (editingId == null) {
        await api.createYonetici({ username: form.username.trim(), password: form.password, adSoyad: form.adSoyad.trim() });
        setMessage("Yönetici hesabı oluşturuldu; yeni başvurular dengeli olarak bu hesaba da atanacaktır.");
      } else {
        await api.updateYonetici(editingId, {
          username: form.username.trim(), password: form.password.trim() || undefined,
          adSoyad: form.adSoyad.trim(), aktif: form.aktif
        });
        setMessage("Yönetici hesabı güncellendi.");
      }
      reset();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Hesap kaydedilemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function distribute() {
    setBusy(true);
    setError("");
    try {
      const result = await api.basvurulariYoneticiyeDagit();
      setMessage(`${result.atananBasvuruSayisi} atamasız başvuru, ${result.aktifYoneticiSayisi} aktif yönetici arasında dengelendi.`);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Başvurular dağıtılamadı.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell home="/admin">
      <h3 className="section">Yönetici hesapları ve başvuru iş yükü</h3>
      <p style={{ color: "var(--muted)", maxWidth: 820, lineHeight: 1.55 }}>
        Gönderilen her yeni başvuru, aktif yöneticiler arasında bekleyen başvuru sayısı en düşük olana atanır.
        Eşitlikte hesap oluşturma sırası kullanılır. Yöneticiler tüm başvuruları görebilir ve karar verebilir;
        yalnızca iş listeleri kendilerine atanan başvurularla filtrelenebilir. Bir hesap pasifleştirildiğinde
        üzerindeki incelemedeki başvurular diğer aktif yöneticilere otomatik aktarılır.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}

      <form className="card" style={{ padding: 20, marginBottom: 18 }} onSubmit={save}>
        <h4 style={{ margin: "0 0 14px" }}>{editingId == null ? "Yeni yönetici" : "Yönetici hesabını düzenle"}</h4>
        <div className="grid-2">
          <div><label>Kullanıcı adı</label><input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required /></div>
          <div><label>Ad soyad / görünen ad</label><input value={form.adSoyad} onChange={(e) => setForm({ ...form, adSoyad: e.target.value })} required /></div>
          <div><label>{editingId == null ? "Şifre" : "Yeni şifre (boş bırakılırsa değişmez)"}</label><input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required={editingId == null} minLength={editingId == null ? 6 : undefined} autoComplete="new-password" /></div>
          {editingId != null && <label style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 26 }}><input type="checkbox" checked={form.aktif} onChange={(e) => setForm({ ...form, aktif: e.target.checked })} style={{ width: "auto", margin: 0 }} /> Hesap aktif</label>}
        </div>
        <div className="row">
          <button className="btn btn-gold" disabled={busy}>{editingId == null ? "Yönetici oluştur" : "Kaydet"}</button>
          {editingId != null && <button type="button" className="btn btn-primary" onClick={reset} disabled={busy}>Vazgeç</button>}
          <button type="button" className="btn btn-navy-ghost" onClick={distribute} disabled={busy}>Atamasız başvuruları dağıt</button>
        </div>
      </form>

      <div className="card" style={{ overflow: "auto" }}>
        <table>
          <thead><tr><th>Yönetici</th><th>Durum</th><th>Bekleyen başvuru</th><th>Oluşturma</th><th></th></tr></thead>
          <tbody>
            {items.map((item) => <tr key={item.id}>
              <td><b>{item.adSoyad}</b><div style={{ color: "var(--muted)" }}>{item.username}</div></td>
              <td><span className={`badge ${item.aktif ? "APPROVED" : "DRAFT"}`}>{item.aktif ? "Aktif" : "Pasif"}</span></td>
              <td>{item.bekleyenBasvuruSayisi}</td>
              <td>{formatDate(item.olusturmaTarihi)}</td>
              <td><button className="btn btn-primary" onClick={() => edit(item)} disabled={busy}>Düzenle</button></td>
            </tr>)}
            {items.length === 0 && <tr><td colSpan={5} style={{ color: "var(--muted)" }}>Henüz yönetici hesabı yok.</td></tr>}
          </tbody>
        </table>
      </div>
    </Shell>
  );
}
