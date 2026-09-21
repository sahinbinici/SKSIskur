import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { Shell, formatDate } from "../components/ui";
import { KayitListSheets } from "./AdminListReports";
import type { KayitListeFiltre, KayitListesi } from "../types";

function kesinDurumLabel(kesinListede: boolean | null) {
  if (kesinListede === true) return "Kesin listede";
  if (kesinListede === false) return "Kesin listede değil";
  return "Karşılaştırma bekliyor";
}

export function AdminKayitPage() {
  const [data, setData] = useState<KayitListesi | null>(null);
  const [filter, setFilter] = useState<KayitListeFiltre>("TUMU");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  function load(nextFilter = filter) {
    return api.kayitListesi(nextFilter).then(setData);
  }

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
  }, []);

  async function uploadKesinList(file: File) {
    if (!window.confirm("Yeni dosya mevcut kesin listesini değiştirir ve karşılaştırmayı yeniden yapar. Devam edilsin mi?")) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await api.uploadKesinListe(file);
      await load();
      setMessage(`Kesin liste yüklendi: ${result.eslesen} eşleşme, ${result.kesinListedeDegil} onaylı başvuru listede yok, ${result.listedeBasvuruEslesmedi} listede eşleşmeyen kayıt.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Kesin liste yüklenemedi.");
    } finally {
      setBusy(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function onayla() {
    if (!window.confirm("Karşılaştırma sonucu onaylansın mı? Onaydan sonra birim dağıtımı yapılabilir.")) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const next = await api.onaylaKesinListe();
      setData(next);
      setMessage("Kesin liste karşılaştırması onaylandı. Birim dağıtımı artık yapılabilir.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Liste onaylanamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function geriAl() {
    if (!window.confirm("Kesin liste yüklemesi ve onayı geri alınsın mı? Dağıtım yapılmamış olmalıdır.")) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const next = await api.geriAlKesinListe();
      setData(next);
      setMessage("Kesin liste sıfırlandı. Yeniden yükleme yapılabilir.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Onay geri alınamadı.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell home="/admin">
      <div className="no-print">
      <h3 className="section">İŞKUR kesin kayıt listesi</h3>
      <p style={{ color: "var(--muted)", maxWidth: 820, lineHeight: 1.55 }}>
        Evrak onayı kesin kayıt değildir. Onaylı başvuruları Excel olarak indirip İŞKUR&apos;a gönderin.
        İŞKUR incelemesinden sonra dönen kesin listeyi yükleyin; sistem onaylı başvurularla otomatik karşılaştırır.
        Karşılaştırmayı onayladıktan sonra birim dağıtımı yapılabilir.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {data?.kesinOnaylandi && (
        <div className="alert alert-ok">
          Kesin liste karşılaştırması onaylandı{data.onaylayanAdmin ? ` (${data.onaylayanAdmin})` : ""}.
          {data.onayTarihi ? ` ${formatDate(data.onayTarihi)}` : ""}
        </div>
      )}
      <div className="grid-5" style={{ marginBottom: 18 }}>
        <Stat title="Onaylı başvuru" value={data?.onayliBasvuru} />
        <Stat title="Kesin listede" value={data?.kesinListede} />
        <Stat title="Kesin listede değil" value={data?.kesinListedeDegil} />
        <Stat title="Karşılaştırma bekleyen" value={data?.kesinKarsilastirmaBekleyen} />
        <Stat title="Listede başvuru yok" value={data?.listedeBasvuruEslesmedi} />
      </div>
      <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>1. Onaylı başvuruları İŞKUR&apos;a gönderin</h4>
        <button
          className="btn btn-gold"
          disabled={!data || data.onayliBasvuru === 0}
          onClick={() => downloadAuthenticatedFile(api.kayitListesiExcelUrl(), "onayli-basvurular.xlsx")
            .catch((err) => setError(err instanceof ApiError ? err.message : "Excel indirilemedi."))}
        >
          Onaylı başvuruları Excel indir
        </button>
      </section>
      <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>2. İŞKUR&apos;dan gelen kesin listeyi yükleyin</h4>
        <div className="row" style={{ alignItems: "center" }}>
          <input
            ref={fileInputRef}
            type="file"
            accept=".xlsx,.xls"
            disabled={busy || data?.kesinOnaylandi}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) void uploadKesinList(file);
            }}
          />
          {data?.kesinListeYuklendi && (
            <span style={{ color: "var(--muted)" }}>
              Son yükleme: {formatDate(data.kesinListeYuklemeTarihi)}
              {data.kesinListeYukleyenAdmin ? ` · ${data.kesinListeYukleyenAdmin}` : ""}
            </span>
          )}
        </div>
      </section>
      <div className="toolbar">
        <select
          value={filter}
          onChange={(e) => {
            const next = e.target.value as KayitListeFiltre;
            setFilter(next);
            load(next).catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
          }}
        >
          <option value="TUMU">Tüm onaylı başvurular</option>
          <option value="KESIN_LISTEDE">Kesin listede</option>
          <option value="KESIN_LISTEDE_DEGIL">Kesin listede değil</option>
          <option value="ONAYLI_BASVURU">Karşılaştırma bekleyen</option>
        </select>
        <button className="btn btn-gold" disabled={busy || !data?.kesinListeYuklendi || data?.kesinOnaylandi} onClick={onayla}>
          Karşılaştırmayı onayla
        </button>
        <button className="btn btn-primary" disabled={busy || (!data?.kesinListeYuklendi && !data?.kesinOnaylandi)} onClick={geriAl}>
          Listeyi sıfırla
        </button>
        <button className="btn btn-secondary" disabled={!data} onClick={() => window.print()}>
          Yazdır / PDF
        </button>
      </div>
      {data && data.listedeEslesmeyenler.length > 0 && (
        <section className="card" style={{ padding: 18, marginBottom: 18, overflow: "auto" }}>
          <h4 style={{ marginTop: 0 }}>Kesin listede olup başvurusu eşleşmeyen kayıtlar</h4>
          <table>
            <thead><tr><th>Ad</th><th>Soyad</th><th>T.C.</th><th>Öğrenci No</th></tr></thead>
            <tbody>
              {data.listedeEslesmeyenler.map((row, index) => (
                <tr key={`${row.ad}-${row.soyad}-${index}`}>
                  <td>{row.ad}</td><td>{row.soyad}</td><td>{row.tcKimlikNo ?? "—"}</td><td>{row.ogrenciNo ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
      <div className="card" style={{ overflow: "auto" }}>
        <table>
          <thead>
            <tr>
              <th>Öğrenci</th>
              <th>Fakülte</th>
              <th>Kesin liste durumu</th>
              <th>Birim</th>
            </tr>
          </thead>
          <tbody>
            {data?.ogrenciler.map((row) => (
              <tr key={row.basvuruId}>
                <td className="clickable" onClick={() => navigate(`/admin/basvuru/${row.basvuruId}`)}>
                  <b>{row.adSoyad}</b>
                  <div style={{ color: "var(--muted)" }}>{row.ogrenciNo}</div>
                </td>
                <td className="clickable" onClick={() => navigate(`/admin/basvuru/${row.basvuruId}`)}>
                  {row.fakulte || row.program || "—"}
                </td>
                <td>{kesinDurumLabel(row.kesinListede)}</td>
                <td>{row.atananBirimAdi || "—"}</td>
              </tr>
            ))}
            {(data?.ogrenciler.length ?? 0) === 0 && (
              <tr>
                <td colSpan={4} style={{ color: "var(--muted)" }}>Bu filtrede öğrenci yok. Önce evrak onayını tamamlayın.</td>
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
