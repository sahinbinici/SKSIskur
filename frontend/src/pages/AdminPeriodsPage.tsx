import { useEffect, useState } from "react";
import { api, ApiError } from "../api";
import { Shell, formatDate } from "../components/ui";
import type { BasvuruDonemi } from "../types";

export function AdminPeriodsPage() {
  const [periods, setPeriods] = useState<BasvuruDonemi[]>([]);
  const [name, setName] = useState("");
  const today = new Date().toISOString().slice(0, 10);
  const [startDate, setStartDate] = useState(today);
  const [endDate, setEndDate] = useState(today);
  const [incomeLimit, setIncomeLimit] = useState("");
  const [activeIncomeLimit, setActiveIncomeLimit] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function load() {
    setPeriods(await api.basvuruDonemleri());
  }

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Dönemler yüklenemedi."));
  }, []);

  async function create() {
    if (!name.trim() || !startDate || !endDate || !incomeLimit || Number(incomeLimit) < 0) return;
    if (endDate < startDate) {
      setError("Bitiş tarihi başlangıç tarihinden önce olamaz.");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api.createBasvuruDonemi(name.trim(), startDate, endDate, Number(incomeLimit));
      setName("");
      setIncomeLimit("");
      await load();
      setMessage("Başvuru dönemi açıldı.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Dönem açılamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function close(period: BasvuruDonemi) {
    if (!window.confirm(`${period.ad} dönemi kapatılsın mı? Birim kullanıcıları bu döneme ait öğrencileri ve puantajları artık göremez.`)) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api.closeBasvuruDonemi(period.id);
      await load();
      setMessage("Başvuru dönemi kapatıldı ve birim erişimi kaldırıldı.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Dönem kapatılamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function saveActiveIncomeLimit() {
    if (!active || !activeIncomeLimit || Number(activeIncomeLimit) < 0) return;
    setBusy(true); setError("");
    try {
      await api.updateBasvuruDonemiGelirLimiti(active.id, Number(activeIncomeLimit));
      await load(); setMessage("Aylık gelir limiti güncellendi.");
    } catch (err) { setError(err instanceof ApiError ? err.message : "Gelir limiti güncellenemedi.");
    } finally { setBusy(false); }
  }

  const active = periods.find((period) => period.aktif);

  return (
    <Shell home="/admin">
      <h3 className="section">Başvuru dönemleri</h3>
      <p style={{ color: "var(--muted)", maxWidth: 760, lineHeight: 1.55 }}>
        Aynı anda yalnızca bir dönem açık olabilir. Dönem açılırken öğrencilerin giriş ve başvuru yapabileceği tarih aralığını belirleyin. Bu aralığın dışında öğrenci oturumları kapatılır. Dönem kapandığında birimler eski dönemin öğrenci ve devam verilerini göremez; yönetici arşivi incelemeye devam edebilir.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {active && <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>Aktif dönem aylık gelir limiti</h4>
        <div className="row"><input type="number" min="0" step="0.01" placeholder={String(active.aylikGelirLimiti ?? 0)} value={activeIncomeLimit} onChange={(e) => setActiveIncomeLimit(e.target.value)} disabled={busy} />
          <button className="btn btn-primary" disabled={busy || !activeIncomeLimit} onClick={saveActiveIncomeLimit}>Limiti kaydet</button></div>
      </section>}
      <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>Yeni dönem aç</h4>
        <div className="row" style={{ alignItems: "end" }}>
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Örn. 2026–2027 Güz Dönemi"
            maxLength={120}
            disabled={busy || Boolean(active)}
            style={{ margin: 0, maxWidth: 360 }}
          />
          <label style={{ margin: 0 }}>
            Başlangıç
            <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} disabled={busy || Boolean(active)} style={{ margin: "4px 0 0" }} />
          </label>
          <label style={{ margin: 0 }}>
            Bitiş
            <input type="date" value={endDate} min={startDate} onChange={(event) => setEndDate(event.target.value)} disabled={busy || Boolean(active)} style={{ margin: "4px 0 0" }} />
          </label>
          <label style={{ margin: 0 }}>
            Aylık gelir limiti (TL)
            <input type="number" min="0" step="0.01" value={incomeLimit} onChange={(event) => setIncomeLimit(event.target.value)} disabled={busy || Boolean(active)} style={{ margin: "4px 0 0" }} />
          </label>
          <button className="btn btn-gold" disabled={busy || Boolean(active) || !name.trim() || !startDate || !endDate} onClick={create}>Dönemi aç</button>
        </div>
        {active && <p style={{ color: "var(--muted)", marginBottom: 0 }}>Önce açık olan “{active.ad}” dönemini kapatın.</p>}
      </section>
      <div className="card" style={{ overflow: "auto" }}>
        <table>
          <thead><tr><th>Dönem</th><th>Öğrenci giriş aralığı</th><th>Gelir limiti</th><th>Durum</th><th>Açılış</th><th>Kapanış</th><th /></tr></thead>
          <tbody>
            {periods.map((period) => (
              <tr key={period.id}>
                <td><b>{period.ad}</b></td>
                <td>{period.ogrenciBaslangicTarihi && period.ogrenciBitisTarihi
                  ? `${period.ogrenciBaslangicTarihi} – ${period.ogrenciBitisTarihi}`
                  : "Tarih tanımlanmadı"}</td>
                <td>{(period.aylikGelirLimiti ?? 0).toLocaleString("tr-TR")} TL</td>
                <td><span className={`badge ${period.ogrenciGirisiAcik ? "APPROVED" : "DRAFT"}`}>{period.ogrenciGirisiAcik ? "Öğrenci girişi açık" : period.aktif ? "Giriş kapalı" : "Dönem kapalı"}</span></td>
                <td>{formatDate(period.olusturmaTarihi)}</td>
                <td>{formatDate(period.kapanisTarihi)}</td>
                <td>{period.aktif && <button className="btn btn-danger" disabled={busy} onClick={() => close(period)}>Dönemi kapat</button>}</td>
              </tr>
            ))}
            {periods.length === 0 && <tr><td colSpan={7} style={{ color: "var(--muted)" }}>Henüz başvuru dönemi yok.</td></tr>}
          </tbody>
        </table>
      </div>
    </Shell>
  );
}
