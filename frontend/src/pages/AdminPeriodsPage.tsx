import { useEffect, useRef, useState } from "react";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { PageHeader } from "../components/PageHeader";
import { Shell, formatDate } from "../components/ui";
import type { BasvuruDalga, BasvuruDonemi, IskurListe } from "../types";

export function AdminPeriodsPage() {
  const confirm = useConfirm();
  const today = new Date().toISOString().slice(0, 10);
  const [periods, setPeriods] = useState<BasvuruDonemi[]>([]);
  const [iskurList, setIskurList] = useState<IskurListe | null>(null);
  const [dalgalar, setDalgalar] = useState<BasvuruDalga[]>([]);
  const [waveStart, setWaveStart] = useState(today);
  const [waveEnd, setWaveEnd] = useState(today);
  const [name, setName] = useState("");
  const [startDate, setStartDate] = useState(today);
  const [endDate, setEndDate] = useState(today);
  const [incomeLimit, setIncomeLimit] = useState("");
  const [activeIncomeLimit, setActiveIncomeLimit] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [iskurQuery, setIskurQuery] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const iskurSearchTimer = useRef<number | undefined>(undefined);
  const skipIskurSearch = useRef(true);

  async function load() {
    const nextPeriods = await api.basvuruDonemleri();
    setPeriods(nextPeriods);
    const activePeriod = nextPeriods.find((period) => period.aktif);
    if (activePeriod) {
      setIskurList(await api.iskurListesi(activePeriod.id, iskurQuery.trim() || undefined));
      setDalgalar(await api.basvuruDalgalar(activePeriod.id));
    } else {
      setIskurList(null);
      setDalgalar([]);
    }
  }

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Dönemler yüklenemedi."));
  }, []);

  useEffect(() => {
    if (skipIskurSearch.current) {
      skipIskurSearch.current = false;
      return;
    }
    const activePeriod = periods.find((period) => period.aktif);
    if (!activePeriod) return;
    window.clearTimeout(iskurSearchTimer.current);
    iskurSearchTimer.current = window.setTimeout(() => {
      api.iskurListesi(activePeriod.id, iskurQuery.trim() || undefined)
        .then(setIskurList)
        .catch((err) => setError(err instanceof ApiError ? err.message : "Liste aranamadı."));
    }, 300);
    return () => window.clearTimeout(iskurSearchTimer.current);
  }, [iskurQuery]);

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
    if (!await confirm({
      title: "Dönemi kapat",
      message: `${period.ad} dönemi kapatılacak. Birim kullanıcıları bu döneme ait öğrencileri artık göremez.`,
      confirmLabel: "Kapat",
      variant: "danger"
    })) return;
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
    if (!await confirm({
      title: "İŞKUR listesini yükle",
      message: "Yeni dosya yalnızca aktif başvuru turunun İŞKUR listesini değiştirir. Dönem boyunca listede yer alan öğrenciler başvuruya uygun kalır.",
      confirmLabel: "Yükle"
    })) return;
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

  async function startNewWave() {
    if (!active) return;
    if (waveEnd < waveStart) {
      setError("Tur bitiş tarihi başlangıçtan önce olamaz.");
      return;
    }
    if (!await confirm({
      title: "Yeni başvuru turu",
      message: "Mevcut turda kesin liste onayı ve imza bildirimi tamamlanmış olmalıdır. Yeni tur açıldığında öğrenci başvuru tarihleri güncellenir.",
      confirmLabel: "Tur aç"
    })) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api.yeniBasvuruTuru(active.id, waveStart, waveEnd);
      await load();
      setMessage("Yeni başvuru turu açıldı. Bu tur için İŞKUR listesini yükleyin.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Yeni tur açılamadı.");
    } finally {
      setBusy(false);
    }
  }

  const active = periods.find((period) => period.aktif);
  const aktifDalga = dalgalar.find((dalga) => dalga.aktif);

  return (
    <Shell home="/admin">
      <PageHeader
        title="Başvuru dönemleri"
        description="Aktif dönemi açın, İŞKUR listesini yükleyin ve öğrenci başvuru penceresini yönetin."
      />
      <p style={{ color: "var(--muted)", maxWidth: 760, lineHeight: 1.55 }}>
        Aynı anda yalnızca bir dönem açık olabilir. Dönem açıldıktan sonra İŞKUR&apos;dan gelen Excel listesini yükleyin; yalnızca listede adı ve soyadı bulunan öğrenciler giriş yapıp başvuru oluşturabilir. Öğrenci giriş tarih aralığı dışında oturum açılamaz.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {active && aktifDalga && <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>Başvuru turları — {active.ad}</h4>
        <p style={{ color: "var(--muted)", marginTop: 0 }}>
          Aktif tur: <b>{aktifDalga.ad}</b>
          {aktifDalga.kesinOnaylandi && aktifDalga.imzaBildirimiGonderildi
            ? " · Dağıtım sonrası boş kontenjan için yeni tur açabilirsiniz."
            : " · Kesin liste ve imza tamamlanmadan yeni tur açılamaz."}
        </p>
        <div style={{ overflow: "auto", marginBottom: 16 }}>
          <table>
            <thead>
              <tr>
                <th>Tur</th><th>Durum</th><th>Öğrenci aralığı</th><th>İŞKUR</th><th>Nihai liste</th><th>Yüklendi</th><th>İmza daveti</th>
              </tr>
            </thead>
            <tbody>
              {dalgalar.map((dalga) => (
                <tr key={dalga.id}>
                  <td><b>{dalga.ad}</b>{dalga.aktif ? " (aktif)" : ""}</td>
                  <td>{dalga.durum === "ACIK" ? "Açık" : "Tamamlandı"}</td>
                  <td>{dalga.ogrenciBaslangicTarihi && dalga.ogrenciBitisTarihi
                    ? `${dalga.ogrenciBaslangicTarihi} – ${dalga.ogrenciBitisTarihi}`
                    : "—"}</td>
                  <td>{dalga.iskurListeKayitSayisi}</td>
                  <td>{dalga.kesinListeKayitSayisi}</td>
                  <td>{dalga.kesinOnaylandi ? "Evet" : "Hayır"}</td>
                  <td>{dalga.imzaBildirimiGonderildi ? "Gönderildi" : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {aktifDalga.kesinOnaylandi && aktifDalga.imzaBildirimiGonderildi && (
          <div className="row" style={{ alignItems: "end" }}>
            <label style={{ margin: 0 }}>
              Yeni tur başlangıç
              <input type="date" value={waveStart} onChange={(e) => setWaveStart(e.target.value)} disabled={busy} style={{ margin: "4px 0 0" }} />
            </label>
            <label style={{ margin: 0 }}>
              Bitiş
              <input type="date" value={waveEnd} min={waveStart} onChange={(e) => setWaveEnd(e.target.value)} disabled={busy} style={{ margin: "4px 0 0" }} />
            </label>
            <button className="btn btn-primary" disabled={busy} onClick={startNewWave}>Yeni tur başlat</button>
          </div>
        )}
      </section>}
      {active && <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>İŞKUR başvuru listesi — {aktifDalga ? aktifDalga.ad : active.ad}</h4>
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
        <div className="filter-panel" style={{ marginBottom: 14 }}>
          <div className="filter-panel-section">
            <h4 className="filter-panel-title">Listede ara</h4>
            <div className="filter-panel-row">
              <label className="filter-label filter-label-grow">
                Öğrenci
                <input
                  placeholder="Ad, soyad, T.C. veya öğrenci no"
                  value={iskurQuery}
                  onChange={(event) => setIskurQuery(event.target.value)}
                />
              </label>
              {iskurQuery && (
                <button type="button" className="btn btn-secondary" onClick={() => setIskurQuery("")}>
                  Temizle
                </button>
              )}
            </div>
            <p className="filter-panel-note" style={{ marginTop: 8 }}>
              {iskurList
                ? iskurQuery.trim()
                  ? `${iskurList.eslesenSayisi ?? iskurList.onizleme.length} kayıt bulundu (toplam ${iskurList.kayitSayisi}).`
                  : `Toplam ${iskurList.kayitSayisi} kayıt.`
                : "Listede ad, soyad, T.C. veya öğrenci numarası ile arayın."}
            </p>
          </div>
        </div>
        {iskurList && iskurQuery.trim() && iskurList.onizleme.length === 0 && (
          <p style={{ color: "var(--muted)", marginBottom: 0 }}>Eşleşen kayıt yok.</p>
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
            {iskurList.eslesenSayisi > iskurList.onizleme.length && (
              <p style={{ color: "var(--muted)", marginBottom: 0 }}>
                İlk {iskurList.onizleme.length} kayıt gösteriliyor (eşleşen {iskurList.eslesenSayisi}, toplam {iskurList.kayitSayisi}).
              </p>
            )}
            {!iskurQuery.trim() && iskurList.kayitSayisi > iskurList.onizleme.length && iskurList.eslesenSayisi === iskurList.kayitSayisi && (
              <p style={{ color: "var(--muted)", marginBottom: 0 }}>
                İlk {iskurList.onizleme.length} kayıt gösteriliyor (toplam {iskurList.kayitSayisi}). Arayarak diğer kayıtları bulun.
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
