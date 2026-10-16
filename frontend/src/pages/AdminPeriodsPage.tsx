import { useEffect, useRef, useState } from "react";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { AdminProcessNav } from "../components/AdminProcessNav";
import { PageHeader } from "../components/PageHeader";
import { Shell, formatDate } from "../components/ui";
import type { BasvuruDalga, BasvuruDonemi } from "../types";

export function AdminPeriodsPage() {
  const confirm = useConfirm();
  const today = new Date().toISOString().slice(0, 10);
  const [periods, setPeriods] = useState<BasvuruDonemi[]>([]);
  const [dalgalar, setDalgalar] = useState<BasvuruDalga[]>([]);
  const [waveStart, setWaveStart] = useState(today);
  const [waveEnd, setWaveEnd] = useState(today);
  const [name, setName] = useState("");
  const [startDate, setStartDate] = useState(today);
  const [endDate, setEndDate] = useState(today);
  const [incomeLimit, setIncomeLimit] = useState("");
  const [editName, setEditName] = useState("");
  const [editStart, setEditStart] = useState(today);
  const [editEnd, setEditEnd] = useState(today);
  const [editLimit, setEditLimit] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [selectedId, setSelectedId] = useState<number | undefined>();
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function load(preferredId?: number) {
    const nextPeriods = await api.basvuruDonemleri();
    setPeriods(nextPeriods);
    const nextId = preferredId
      ?? selectedId
      ?? nextPeriods.find((period) => period.aktif)?.id
      ?? nextPeriods[0]?.id;
    setSelectedId(nextId);
    const selected = nextPeriods.find((period) => period.id === nextId);
    if (selected) {
      setEditName(selected.ad);
      setEditStart(selected.ogrenciBaslangicTarihi ?? today);
      setEditEnd(selected.ogrenciBitisTarihi ?? today);
      setEditLimit(String(selected.aylikGelirLimiti ?? ""));
      setDalgalar(await api.basvuruDalgalar(selected.id));
    } else {
      setDalgalar([]);
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
      const created = await api.createBasvuruDonemi(name.trim(), startDate, endDate, Number(incomeLimit));
      setIncomeLimit("");
      await load(created.id);
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

  async function saveSelected() {
    if (!selected || !editName.trim() || !editStart || !editEnd || !editLimit || Number(editLimit) < 0) return;
    if (editEnd < editStart) {
      setError("Bitiş tarihi başlangıç tarihinden önce olamaz.");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api.updateBasvuruDonemi(selected.id, editName.trim(), editStart, editEnd, Number(editLimit));
      await load(selected.id);
      setMessage("Dönem bilgileri kaydedildi.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Dönem güncellenemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(period: BasvuruDonemi) {
    if (period.aktif) return;
    if (!await confirm({
      title: "Dönemi sil",
      message: `${period.ad} dönemi ve bu döneme ait başvurular, listeler ve puantaj kayıtları kalıcı olarak silinecek.`,
      confirmLabel: "Sil",
      variant: "danger"
    })) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api.deleteBasvuruDonemi(period.id);
      setSelectedId(undefined);
      await load();
      setMessage("Başvuru dönemi silindi.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Dönem silinemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function uploadIskurList(file: File) {
    if (!selected) return;
    if (!await confirm({
      title: "İŞKUR listesini yükle",
      message: "Yeni dosya yalnızca aktif başvuru turunun İŞKUR listesini değiştirir. Dönem boyunca listede yer alan öğrenciler başvuruya uygun kalır.",
      confirmLabel: "Yükle"
    })) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await api.uploadIskurListesi(selected.id, file);
      await load(selected.id);
      setMessage(`${result.kayitSayisi} öğrenci kaydı yüklendi${result.atlananTekrar ? ` (${result.atlananTekrar} tekrar atlandı)` : ""}.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "İŞKUR listesi yüklenemedi.");
    } finally {
      setBusy(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function startNewWave() {
    if (!selected) return;
    if (waveEnd < waveStart) {
      setError("Tur bitiş tarihi başlangıçtan önce olamaz.");
      return;
    }
    if (!await confirm({
      title: "Yeni başvuru turu",
      message: "Seçili kapalı dönem için yeni tur açılacak; dönem yeniden öğrenci girişine açılır ve başvuru tarihleri güncellenir. Bu tur için İŞKUR listesini yeniden yüklemeniz gerekir.",
      confirmLabel: "Tur aç"
    })) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api.yeniBasvuruTuru(selected.id, waveStart, waveEnd);
      await load(selected.id);
      setMessage("Yeni başvuru turu açıldı. Bu tur için İŞKUR listesini yükleyin.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Yeni tur açılamadı.");
    } finally {
      setBusy(false);
    }
  }

  const active = periods.find((period) => period.aktif);
  const selected = periods.find((period) => period.id === selectedId) ?? null;
  const aktifDalga = dalgalar.find((dalga) => dalga.aktif);
  const turHazir = Boolean(
    aktifDalga?.kesinOnaylandi && aktifDalga?.imzaBildirimiGonderildi
  );
  const yeniTurAcilabilir = Boolean(selected && !selected.aktif && turHazir);

  return (
    <Shell home="/admin">
      <PageHeader
        title="Başvuru dönemi aç"
        description="Dönem açın, geçmiş dönemleri düzenleyin veya kapalı dönemleri silin. Öğrenci girişi yalnızca açık dönemde çalışır."
      />
      <AdminProcessNav />
      <p style={{ color: "var(--muted)", maxWidth: 760, lineHeight: 1.55 }}>
        Aynı anda yalnızca bir dönem açık olabilir. Dönem açıldıktan sonra İŞKUR&apos;dan gelen Excel listesini yükleyin; yalnızca listede adı ve soyadı bulunan öğrenciler giriş yapıp başvuru oluşturabilir. Öğrenci giriş tarih aralığı dışında oturum açılamaz.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {periods.length > 0 && (
        <label className="filter-label" style={{ marginBottom: 16 }}>
          Düzenlenecek dönem
          <select
            value={selectedId ?? ""}
            onChange={(event) => void load(Number(event.target.value) || undefined)}
          >
            {periods.map((period) => (
              <option key={period.id} value={period.id}>
                {period.ad}{period.aktif ? " (açık)" : " (kapalı)"}
              </option>
            ))}
          </select>
        </label>
      )}
      {selected && aktifDalga && <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>Başvuru turları — {selected.ad}</h4>
        <p style={{ color: "var(--muted)", marginTop: 0 }}>
          Aktif tur: <b>{aktifDalga.ad}</b>
          {turHazir && selected.aktif
            ? " · Yeni tur için önce bu dönemi «Kapat» ile kapatın; ardından kapalı dönem seçili kalarak tur açın."
            : turHazir && !selected.aktif
              ? " · Dönem kapalı; yeni tur başlatabilirsiniz."
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
        {yeniTurAcilabilir && (
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
      {selected && <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>İŞKUR başvuru listesi — {aktifDalga ? aktifDalga.ad : selected.ad}</h4>
        <p style={{ color: "var(--muted)", marginTop: 0 }}>
          Bu liste başvuranlar değil; İŞKUR&apos;dan gelen uygunluk listesidir. Kimlerin başvuru yaptığını <b>Başvuru</b> sekmesinde görün. Excel dosyasında en az <b>Ad</b> ve <b>Soyad</b> sütunları olmalıdır.
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
          <span className={`badge ${selected.iskurListeYuklendi ? "APPROVED" : "RETURNED"}`}>
            {selected.iskurListeYuklendi ? `${selected.iskurListeKayitSayisi} kayıt yüklü` : "Liste yüklenmedi"}
          </span>
          {selected.iskurListeYuklendi && (
            <button
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => downloadAuthenticatedFile(api.iskurListesiExcelUrl(selected.id), "iskur-basvuru-listesi.xlsx")
                .catch((err) => setError(err instanceof ApiError ? err.message : "Excel indirilemedi."))}
            >
              Excel indir
            </button>
          )}
        </div>
        {selected.iskurListeYuklemeTarihi && (
          <p style={{ color: "var(--muted)", marginBottom: 0 }}>
            Son yükleme: {formatDate(selected.iskurListeYuklemeTarihi)}
          </p>
        )}
      </section>}
      {selected && <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>Dönemi düzenle — {selected.ad}</h4>
        {!selected.aktif && <p style={{ color: "var(--muted)", marginTop: 0 }}>Kapalı dönem. Öğrenci girişi kapalıdır; buradan tarih, ad ve liste bilgilerini güncelleyebilirsiniz.</p>}
        <div className="row" style={{ alignItems: "end" }}>
          <input value={editName} onChange={(event) => setEditName(event.target.value)} maxLength={120} disabled={busy} style={{ margin: 0, maxWidth: 360 }} />
          <label style={{ margin: 0 }}>
            Başvuru Başlangıç
            <input type="date" value={editStart} onChange={(event) => setEditStart(event.target.value)} disabled={busy} style={{ margin: "4px 0 0" }} />
          </label>
          <label style={{ margin: 0 }}>
            Başvuru Bitiş
            <input type="date" value={editEnd} min={editStart} onChange={(event) => setEditEnd(event.target.value)} disabled={busy} style={{ margin: "4px 0 0" }} />
          </label>
          <label style={{ margin: 0 }}>
            Aylık gelir limiti (TL)
            <input type="number" min="0" step="0.01" value={editLimit} onChange={(event) => setEditLimit(event.target.value)} disabled={busy} style={{ margin: "4px 0 0" }} />
          </label>
          <button className="btn btn-primary" disabled={busy || !editName.trim() || !editStart || !editEnd || !editLimit} onClick={() => void saveSelected()}>Kaydet</button>
        </div>
      </section>}
      {!active && (
        <section className="card" style={{ padding: 18, marginBottom: 18 }}>
          <h4 style={{ marginTop: 0 }}>Yeni dönem aç</h4>
          <div className="row" style={{ alignItems: "end" }}>
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Örn. 2026–2027 Güz Dönemi"
              maxLength={120}
              disabled={busy}
              style={{ margin: 0, maxWidth: 360 }}
            />
            <label style={{ margin: 0 }}>
              Başvuru Başlangıç
              <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} disabled={busy} style={{ margin: "4px 0 0" }} />
            </label>
            <label style={{ margin: 0 }}>
              Başvuru Bitiş
              <input type="date" value={endDate} min={startDate} onChange={(event) => setEndDate(event.target.value)} disabled={busy} style={{ margin: "4px 0 0" }} />
            </label>
            <label style={{ margin: 0 }}>
              Aylık gelir limiti (TL)
              <input type="number" min="0" step="0.01" value={incomeLimit} onChange={(event) => setIncomeLimit(event.target.value)} disabled={busy} style={{ margin: "4px 0 0" }} />
            </label>
            <button className="btn btn-gold" disabled={busy || !name.trim() || !startDate || !endDate} onClick={create}>Dönemi aç</button>
          </div>
        </section>
      )}
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
                <td style={{ whiteSpace: "nowrap" }}>
                  {!period.aktif && (
                    <button className="btn btn-secondary btn-compact" disabled={busy} onClick={() => void load(period.id)}>Düzenle</button>
                  )}
                  {period.aktif
                    ? <button className="btn btn-danger btn-compact" disabled={busy} onClick={() => void close(period)}>Kapat</button>
                    : <button className="btn btn-danger btn-compact" disabled={busy} onClick={() => void remove(period)}>Sil</button>}
                </td>
              </tr>
            ))}
            {periods.length === 0 && <tr><td colSpan={8} style={{ color: "var(--muted)" }}>Henüz başvuru dönemi yok.</td></tr>}
          </tbody>
        </table>
      </div>
    </Shell>
  );
}
