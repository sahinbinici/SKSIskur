import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../api";
import { Shell, formatDate } from "../components/ui";
import { KayitListSheets } from "./AdminListReports";
import { KAYIT_LABEL, type KayitListesi, type KayitTuru } from "../types";

export function AdminKayitPage() {
  const [data, setData] = useState<KayitListesi | null>(null);
  const [filter, setFilter] = useState<"ALL" | "BEKLEYEN" | KayitTuru>("ALL");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  function load() {
    return api.kayitListesi().then(setData);
  }

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
  }, []);

  const rows = useMemo(() => {
    const list = data?.ogrenciler ?? [];
    if (filter === "ALL") return list;
    if (filter === "BEKLEYEN") return list.filter((row) => !row.kayitTuru);
    return list.filter((row) => row.kayitTuru === filter);
  }, [data, filter]);

  async function setTur(basvuruId: number, tur: KayitTuru) {
    setBusy(true);
    setError("");
    try {
      await api.setKayitTuru(basvuruId, tur);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Kayıt türü güncellenemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function onayla() {
    if (!window.confirm("Kesin ve yedek liste kilitlensin mi? Onaydan sonra birim dağıtımı açılır.")) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const next = await api.onaylaKesinListe();
      setData(next);
      setMessage("Kesin liste onaylandı. Birim dağıtımı artık yapılabilir.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Liste onaylanamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function geriAl() {
    if (!window.confirm("Kesin liste onayı geri alınsın mı? Dağıtım yapılmamış olmalıdır.")) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const next = await api.geriAlKesinListe();
      setData(next);
      setMessage("Kesin liste onayı geri alındı. Kayıt türleri yeniden düzenlenebilir.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Onay geri alınamadı.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell home="/admin">
      <div className="no-print">
      <h3 className="section">Kesin ve yedek kayıt listesi</h3>
      <p style={{ color: "var(--muted)", maxWidth: 760, lineHeight: 1.55 }}>
        Evrak onayı kesin kayıt değildir. Bu ekranda onaylanan öğrenciler kesin veya yedek olarak işaretlenir.
        Liste onaylandıktan sonra yalnızca kesin kayıttaki öğrenciler birimlere dağıtılır.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {data?.kesinOnaylandi && (
        <div className="alert alert-ok">
          Kesin liste onaylandı{data.onaylayanAdmin ? ` (${data.onaylayanAdmin})` : ""}.
          {data.onayTarihi ? ` ${formatDate(data.onayTarihi)}` : ""}
        </div>
      )}
      <div className="grid-4" style={{ marginBottom: 18 }}>
        <Stat title="Onaylı evrak" value={data?.ogrenciler.length} />
        <Stat title="Bekleyen" value={data?.bekleyen} />
        <Stat title="Kesin" value={data?.kesin} />
        <Stat title="Yedek" value={data?.yedek} />
      </div>
      <div className="toolbar">
        <select value={filter} onChange={(e) => setFilter(e.target.value as typeof filter)}>
          <option value="ALL">Tümü</option>
          <option value="BEKLEYEN">Henüz işaretlenmeyen</option>
          <option value="KESIN">Kesin kayıt</option>
          <option value="YEDEK">Yedek</option>
        </select>
        <button className="btn btn-gold" disabled={busy || data?.kesinOnaylandi} onClick={onayla}>
          Kesin listeyi onayla
        </button>
        <button className="btn btn-primary" disabled={busy || !data?.kesinOnaylandi} onClick={geriAl}>
          Onayı geri al
        </button>
        <button className="btn btn-gold" disabled={!data} onClick={() => window.print()}>
          Yazdır / PDF
        </button>
      </div>
      <div className="card" style={{ overflow: "auto" }}>
        <table>
          <thead>
            <tr>
              <th>Öğrenci</th>
              <th>Fakülte</th>
              <th>Kayıt türü</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.basvuruId}>
                <td className="clickable" onClick={() => navigate(`/admin/basvuru/${row.basvuruId}`)}>
                  <b>{row.adSoyad}</b>
                  <div style={{ color: "var(--muted)" }}>{row.ogrenciNo}</div>
                </td>
                <td className="clickable" onClick={() => navigate(`/admin/basvuru/${row.basvuruId}`)}>
                  {row.fakulte || row.program || "—"}
                </td>
                <td onClick={(e) => e.stopPropagation()}>
                  <select
                    value={row.kayitTuru ?? ""}
                    disabled={busy || data?.kesinOnaylandi}
                    onChange={(e) => {
                      const value = e.target.value as KayitTuru;
                      if (value) void setTur(row.basvuruId, value);
                    }}
                    style={{ margin: 0, maxWidth: 180 }}
                  >
                    <option value="">Seçiniz</option>
                    <option value="KESIN">{KAYIT_LABEL.KESIN}</option>
                    <option value="YEDEK">{KAYIT_LABEL.YEDEK}</option>
                  </select>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={3} style={{ color: "var(--muted)" }}>Bu filtrede öğrenci yok. Önce evrak onayını tamamlayın.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      </div>
      {data && <KayitListSheets data={data} />}
    </Shell>
  );
}

function Stat({ title, value }: { title: string; value?: number }) {
  return (
    <div className="card stat">
      <b>{value ?? "—"}</b>
      <span>{title}</span>
    </div>
  );
}
