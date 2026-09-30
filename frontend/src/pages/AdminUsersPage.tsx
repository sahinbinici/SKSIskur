import { FormEvent, useEffect, useState } from "react";
import { api, ApiError } from "../api";
import { useAuth } from "../auth";
import { PageHeader } from "../components/PageHeader";
import { Shell, formatDate } from "../components/ui";
import type { AdminRole, AdminUser } from "../types";
import { isSuperAdmin } from "../types";

const emptyForm = { username: "", password: "", adSoyad: "", aktif: true, rol: "YONETICI" as AdminRole };

const ROL_LABELS: Record<AdminRole, string> = {
  SUPER_ADMIN: "Ana yönetici",
  YONETICI: "Yönetici"
};

export function AdminUsersPage() {
  const { session } = useAuth();
  const superAdmin = isSuperAdmin(session);
  const [items, setItems] = useState<AdminUser[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const editingSelf = editingId != null && items.some((item) => item.id === editingId && item.benimHesabim);

  const load = () => api.yoneticiler().then(setItems);

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Yönetici hesapları alınamadı."));
  }, []);

  function reset() {
    setEditingId(null);
    setForm(emptyForm);
  }

  function edit(item: AdminUser) {
    if (!superAdmin) return;
    setEditingId(item.id);
    setForm({
      username: item.username,
      adSoyad: item.adSoyad,
      password: "",
      aktif: item.aktif,
      rol: item.rol
    });
    setError("");
    setMessage("");
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!superAdmin) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      if (editingId == null) {
        await api.createYonetici({ username: form.username.trim(), password: form.password, adSoyad: form.adSoyad.trim() });
        setMessage("Yönetici hesabı oluşturuldu; yeni başvurular dengeli olarak bu hesaba da atanacaktır.");
      } else {
        await api.updateYonetici(editingId, {
          username: form.username.trim(),
          password: editingSelf ? undefined : form.password.trim() || undefined,
          adSoyad: form.adSoyad.trim(),
          aktif: editingSelf ? true : form.aktif,
          rol: editingSelf ? undefined : form.rol
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
      <PageHeader
        title="Yönetici hesapları"
        description="SKS yöneticilerini yönetin ve başvuru iş yükünü görüntüleyin."
      />
      <p style={{ color: "var(--muted)", maxWidth: 820, lineHeight: 1.55 }}>
        Gönderilen her yeni başvuru, aktif yöneticiler arasında bekleyen başvuru sayısı en düşük olana atanır.
        Yalnızca <b>ana yönetici</b> yeni hesap açabilir ve diğer yöneticileri düzenleyebilir.
        Yöneticiler kendi hesaplarını pasifleştiremez veya bu ekrandan kendi şifrelerini değiştiremez.
      </p>

      {!superAdmin && (
        <div className="alert" style={{ marginBottom: 16 }}>
          Bu sayfayı salt okunur görüntülüyorsunuz. Hesap yönetimi yalnızca ana yönetici tarafından yapılabilir.
          Rol bilginiz güncel değilse çıkış yapıp tekrar giriş yapın.
        </div>
      )}

      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}

      {superAdmin && (
        <form className="card" style={{ padding: 20, marginBottom: 18 }} onSubmit={save}>
          <h4 style={{ margin: "0 0 14px" }}>{editingId == null ? "Yeni yönetici" : "Yönetici hesabını düzenle"}</h4>
          <div className="grid-2">
            <div><label>Kullanıcı adı</label><input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required /></div>
            <div><label>Ad soyad / görünen ad</label><input value={form.adSoyad} onChange={(e) => setForm({ ...form, adSoyad: e.target.value })} required /></div>
            {!editingSelf && (
              <div>
                <label>{editingId == null ? "Şifre" : "Yeni şifre (boş bırakılırsa değişmez)"}</label>
                <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required={editingId == null} minLength={editingId == null ? 6 : undefined} autoComplete="new-password" />
              </div>
            )}
            {editingId != null && !editingSelf && (
              <>
                <label style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 26 }}>
                  <input type="checkbox" checked={form.aktif} onChange={(e) => setForm({ ...form, aktif: e.target.checked })} style={{ width: "auto", margin: 0 }} />
                  Hesap aktif
                </label>
                <div>
                  <label>Rol</label>
                  <select value={form.rol} onChange={(e) => setForm({ ...form, rol: e.target.value as AdminRole })}>
                    <option value="YONETICI">Yönetici</option>
                    <option value="SUPER_ADMIN">Ana yönetici</option>
                  </select>
                </div>
              </>
            )}
            {editingSelf && (
              <div style={{ color: "var(--muted)", alignSelf: "end" }}>
                Kendi hesabınızda yalnızca kullanıcı adı ve görünen ad güncellenebilir.
              </div>
            )}
          </div>
          <div className="row">
            <button className="btn btn-gold" disabled={busy}>{editingId == null ? "Yönetici oluştur" : "Kaydet"}</button>
            {editingId != null && <button type="button" className="btn btn-primary" onClick={reset} disabled={busy}>Vazgeç</button>}
            <button type="button" className="btn btn-navy-ghost" onClick={distribute} disabled={busy}>Atamasız başvuruları dağıt</button>
          </div>
        </form>
      )}

      {!superAdmin && (
        <div className="row" style={{ marginBottom: 18 }}>
          <button type="button" className="btn btn-navy-ghost" onClick={distribute} disabled={busy}>Atamasız başvuruları dağıt</button>
        </div>
      )}

      <div className="card" style={{ overflow: "auto" }}>
        <table>
          <thead><tr><th>Yönetici</th><th>Rol</th><th>Durum</th><th>Bekleyen başvuru</th><th>Oluşturma</th>{superAdmin && <th></th>}</tr></thead>
          <tbody>
            {items.map((item) => <tr key={item.id}>
              <td>
                <b>{item.adSoyad}</b>
                <div style={{ color: "var(--muted)" }}>{item.username}</div>
                {item.benimHesabim && <div style={{ color: "var(--gold)", fontSize: "0.9em" }}>Sizin hesabınız</div>}
              </td>
              <td>{ROL_LABELS[item.rol] ?? item.rol}</td>
              <td><span className={`badge ${item.aktif ? "APPROVED" : "DRAFT"}`}>{item.aktif ? "Aktif" : "Pasif"}</span></td>
              <td>{item.bekleyenBasvuruSayisi}</td>
              <td>{formatDate(item.olusturmaTarihi)}</td>
              {superAdmin && (
                <td><button className="btn btn-primary" onClick={() => edit(item)} disabled={busy}>Düzenle</button></td>
              )}
            </tr>)}
            {items.length === 0 && <tr><td colSpan={superAdmin ? 6 : 5} style={{ color: "var(--muted)" }}>Henüz yönetici hesabı yok.</td></tr>}
          </tbody>
        </table>
      </div>
    </Shell>
  );
}
