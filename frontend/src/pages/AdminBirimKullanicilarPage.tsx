import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, ApiError } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { PageHeader } from "../components/PageHeader";
import { Shell, formatDate } from "../components/ui";
import type { BirimKullanici, WorkUnit } from "../types";

const emptyForm = {
  birimKodu: "",
  username: "",
  adSoyad: "",
  password: "",
  aktif: true
};

export function AdminBirimKullanicilarPage() {
  const confirm = useConfirm();
  const [units, setUnits] = useState<WorkUnit[]>([]);
  const [items, setItems] = useState<BirimKullanici[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  function load() {
    return Promise.all([api.adminUnits(), api.birimKullanicilar()]).then(([nextUnits, nextItems]) => {
      setUnits(nextUnits);
      setItems(nextItems);
    });
  }

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Birim hesapları alınamadı."));
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLocaleLowerCase("tr-TR");
    if (!q) return items;
    return items.filter((item) =>
      [item.username, item.adSoyad, item.birimAdi, item.birimKodu].some((value) =>
        value.toLocaleLowerCase("tr-TR").includes(q)
      )
    );
  }, [items, query]);

  function resetForm() {
    setEditingId(null);
    setForm(emptyForm);
  }

  function onUnitChange(birimKodu: string) {
    const unit = units.find((item) => item.kod === birimKodu);
    setForm((prev) => ({
      ...prev,
      birimKodu,
      username: editingId == null ? (unit?.kod ?? "") : prev.username,
      adSoyad: editingId == null ? (unit?.ad ?? prev.adSoyad) : prev.adSoyad
    }));
  }

  function startEdit(item: BirimKullanici) {
    setEditingId(item.id);
    setForm({
      birimKodu: item.birimKodu,
      username: item.username,
      adSoyad: item.adSoyad,
      password: "",
      aktif: item.aktif
    });
    setError("");
    setMessage("");
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      if (editingId == null) {
        await api.createBirimKullanici({
          username: form.username.trim(),
          password: form.password,
          adSoyad: form.adSoyad.trim(),
          birimKodu: form.birimKodu
        });
        setMessage("Birim hesabı oluşturuldu.");
      } else {
        await api.updateBirimKullanici(editingId, {
          username: form.username.trim(),
          password: form.password.trim() || undefined,
          adSoyad: form.adSoyad.trim(),
          birimKodu: form.birimKodu,
          aktif: form.aktif
        });
        setMessage("Birim hesabı güncellendi.");
      }
      resetForm();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "İşlem tamamlanamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function toggleAktif(item: BirimKullanici) {
    setBusy(true);
    setError("");
    try {
      await api.updateBirimKullanici(item.id, {
        username: item.username,
        adSoyad: item.adSoyad,
        birimKodu: item.birimKodu,
        aktif: !item.aktif
      });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Durum güncellenemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(item: BirimKullanici) {
    if (!await confirm({
      title: "Hesabı sil",
      message: `${item.username} hesabı kalıcı olarak silinecek.`,
      confirmLabel: "Sil",
      variant: "danger"
    })) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api.deleteBirimKullanici(item.id);
      if (editingId === item.id) resetForm();
      setMessage("Birim hesabı silindi.");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Hesap silinemedi.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell home="/admin">
      <PageHeader title="Birim hesapları" description="Birim kullanıcılarını oluşturun, güncelleyin ve erişimlerini yönetin." />
      <p style={{ color: "var(--muted)", maxWidth: 760, lineHeight: 1.55 }}>
        Her birime bir veya daha fazla kullanıcı tanımlanır. Bu hesapla giren kişi yalnızca kendi birimine
        atanan öğrencilerin EK-6 ve puantajını görür. Şifreler saklanmaz; yalnızca yeni şifre yazılabilir.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}

      <form className="card" style={{ padding: 20, marginBottom: 18 }} onSubmit={onSubmit}>
        <h4 style={{ margin: "0 0 14px" }}>{editingId == null ? "Yeni hesap" : "Hesabı düzenle"}</h4>
        <div className="grid-2">
          <div>
            <label>Birim</label>
            <select value={form.birimKodu} onChange={(e) => onUnitChange(e.target.value)} required>
              <option value="">Seçiniz</option>
              {units.map((unit) => (
                <option key={unit.kod} value={unit.kod}>{unit.ad} ({unit.kod})</option>
              ))}
            </select>
          </div>
          <div>
            <label>Kullanıcı adı</label>
            <input
              value={form.username}
              onChange={(e) => setForm((prev) => ({ ...prev, username: e.target.value }))}
              autoComplete="off"
              required
            />
          </div>
          <div>
            <label>Ad soyad / görünen ad</label>
            <input
              value={form.adSoyad}
              onChange={(e) => setForm((prev) => ({ ...prev, adSoyad: e.target.value }))}
              required
            />
          </div>
          <div>
            <label>{editingId == null ? "Şifre" : "Yeni şifre (boş bırakılırsa değişmez)"}</label>
            <input
              type="password"
              value={form.password}
              onChange={(e) => setForm((prev) => ({ ...prev, password: e.target.value }))}
              autoComplete="new-password"
              required={editingId == null}
              minLength={editingId == null ? 6 : undefined}
            />
          </div>
        </div>
        {editingId != null && (
          <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <input
              type="checkbox"
              checked={form.aktif}
              onChange={(e) => setForm((prev) => ({ ...prev, aktif: e.target.checked }))}
              style={{ width: "auto", margin: 0 }}
            />
            Hesap aktif
          </label>
        )}
        <div className="row">
          <button className="btn btn-gold" disabled={busy}>
            {editingId == null ? "Hesap oluştur" : "Kaydet"}
          </button>
          {editingId != null && (
            <button type="button" className="btn btn-primary" disabled={busy} onClick={resetForm}>
              Vazgeç
            </button>
          )}
        </div>
      </form>

      <div className="toolbar">
        <input
          placeholder="Kullanıcı, birim veya ad ara"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>
      <div className="card" style={{ overflow: "auto" }}>
        <table>
          <thead>
            <tr>
              <th>Kullanıcı</th>
              <th>Birim</th>
              <th>Durum</th>
              <th>Oluşturma</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item) => (
              <tr key={item.id}>
                <td>
                  <b>{item.username}</b>
                  <div style={{ color: "var(--muted)" }}>{item.adSoyad}</div>
                </td>
                <td>
                  {item.birimAdi}
                  <div style={{ color: "var(--muted)" }}>Kod {item.birimKodu}</div>
                </td>
                <td>
                  <span className={`badge ${item.aktif ? "APPROVED" : "DRAFT"}`}>
                    {item.aktif ? "Aktif" : "Pasif"}
                  </span>
                </td>
                <td>{formatDate(item.olusturmaTarihi)}</td>
                <td>
                  <div className="row" style={{ justifyContent: "flex-end" }}>
                    <button type="button" className="btn btn-primary" disabled={busy} onClick={() => startEdit(item)}>
                      Düzenle
                    </button>
                    <button type="button" className="btn btn-navy-ghost" disabled={busy} onClick={() => toggleAktif(item)}>
                      {item.aktif ? "Pasifleştir" : "Aktifleştir"}
                    </button>
                    <button type="button" className="btn btn-danger" disabled={busy} onClick={() => remove(item)}>
                      Sil
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={5} style={{ color: "var(--muted)" }}>Henüz birim hesabı yok.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </Shell>
  );
}
