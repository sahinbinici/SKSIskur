import { useEffect, useRef, useState } from "react";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { Shell, formatDate } from "../components/ui";
import type { BasvuruDonemi, IskurListe } from "../types";

export function AdminPeriodsPage() {
  const [periods, setPeriods] = useState<BasvuruDonemi[]>([]);
  const [iskurList, setIskurList] = useState<IskurListe | null>(null);
  const [name, setName] = useState("");
  const today = new Date().toISOString().slice(0, 10);
  const [startDate, setStartDate] = useState(today);
  const [endDate, setEndDate] = useState(today);
  const [incomeLimit, setIncomeLimit] = useState("");
  const [activeIncomeLimit, setActiveIncomeLimit] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function load() {
    const nextPeriods = await api.basvuruDonemleri();
    setPeriods(nextPeriods);
    const activePeriod = nextPeriods.find((period) => period.aktif);
    if (activePeriod) {
      setIskurList(await api.iskurListesi(activePeriod.id));
    } else {
      setIskurList(null);
    }
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
      setMessage("Başvuru dönemi açıldı. Öğrenci girişi için İŞKUR listesini yükleyin.");
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

  async function uploadIskurList(file: File) {
    if (!active) return;
    if (!window.confirm("Yeni dosya mevcut İŞKUR listesinin tamamını değiştirir. Devam edilsin mi?")) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await api.uploadIskurListesi(active.id, file);
      await load();
      setMessage(`${result.kayitSayisi} öğrenci kaydı yüklendi${result.atlananTekrar ? ` (${result.atlananTekrar} tekrar atlandı)` : ""}.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "İŞKUR listesi yüklenemedi.");
    } finally {
      setBusy(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  const active = periods.find((period) => period.aktif);

  return (
    <Shell home="/admin">
      <h3 className="section">Başvuru dönemleri</h3>
      <p style={{ color: "var(--muted)", maxWidth: 760, lineHeight: 1.55 }}>
        Aynı anda yalnızca bir dönem açık olabilir. Dönem açıldıktan sonra İŞKUR&apos;dan gelen Excel listesini yükleyin; yalnızca listede adı ve soyadı bulunan öğrenciler giriş yapıp başvuru oluşturabilir. Öğrenci giriş tarih aralığı dışında oturum açılamaz.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {active && <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>İŞKUR başvuru listesi — {active.ad}</h4>
        <p style={{ color: "var(--muted)", marginTop: 0 }}>
          Excel dosyasında en az <b>Ad</b> ve <b>Soyad</b> sütunları olmalıdır. Varsa T.C. Kimlik No ve Öğrenci No eşleştirmeyi güçlendirir.
        </p>
        <div className="row" style={{ alignItems: "center", marginBottom: 12 }}>
          <input
            ref={fileInputRef}
            type="file"
            accept=".xlsx,.xls"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) uploadIskurList(file);
            }}
          />
          <span className={`badge ${active.iskurListeYuklendi ? "APPROVED" : "RETURNED"}`}>
            {active.iskurListeYuklendi ? `${active.iskurListeKayitSayisi} kayıt yüklü` : "Liste yüklenmedi"}
          </span>
          {active.iskurListeYuklendi && (
            <button
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => downloadAuthenticatedFile(api.iskurListesiExcelUrl(active.id), "iskur-basvuru-listesi.xlsx")
                .catch((err) => setError(err instanceof ApiError ? err.message : "Excel indirilemedi."))}
            >
              Excel indir
            </button>
          )}
        </div>
        {active.iskurListeYuklemeTarihi && (
          <p style={{ color: "var(--muted)", margin: "0 0 12px" }}>
            Son yükleme: {formatDate(active.iskurListeYuklemeTarihi)}
          </p>
        )}
        {iskurList && iskurList.onizleme.length > 0 && (
          <div style={{ overflow: "auto" }}>
            <table>
              <thead>
                <tr><th>Ad</th><th>Soyad</th><th>T.C.</th><th>Öğrenci No</th></tr>
              </thead>
              <tbody>
                {iskurList.onizleme.map((row, index) => (
                  <tr key={`${row.ad}-${row.soyad}-${index}`}>
                    <td>{row.ad}</td>
                    <td>{row.soyad}</td>
                    <td>{row.tcKimlikNo ?? "—"}</td>
                    <td>{row.ogrenciNo ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {iskurList.kayitSayisi > iskurList.onizleme.length && (
              <p style={{ color: "var(--muted)", marginBottom: 0 }}>
                İlk {iskurList.onizleme.length} kayıt gösteriliyor (toplam {iskurList.kayitSayisi}).
              </p>
            )}
          </div>
        )}
      </section>}
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
          <thead><tr><th>Dönem</th><th>İŞKUR listesi</th><th>Öğrenci giriş aralığı</th><th>Gelir limiti</th><th>Durum</th><th>Açılış</th><th>Kapanış</th><th /></tr></thead>
          <tbody>
            {periods.map((period) => (
              <tr key={period.id}>
                <td><b>{period.ad}</b></td>
                <td>{period.iskurListeYuklendi ? `${period.iskurListeKayitSayisi} kayıt` : "—"}</td>
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
            {periods.length === 0 && <tr><td colSpan={8} style={{ color: "var(--muted)" }}>Henüz başvuru dönemi yok.</td></tr>}
          </tbody>
        </table>
      </div>
    </Shell>
  );
}
