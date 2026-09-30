import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { EmptyState } from "../components/EmptyState";
import { PageHeader } from "../components/PageHeader";
import { Shell, formatDate } from "../components/ui";import { KayitListSheets } from "./AdminListReports";
import type { KayitListeFiltre, KayitListesi } from "../types";

function kesinDurumLabel(kesinListede: boolean | null) {
  if (kesinListede === true) return "Kesin listede";
  if (kesinListede === false) return "Kesin listede değil";
  return "Karşılaştırma bekliyor";
}

export function AdminKayitPage() {
  const confirm = useConfirm();  const [data, setData] = useState<KayitListesi | null>(null);
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
    const ok = await confirm({
      title: "Kesin listeyi yükle",
      message: "Yeni dosya mevcut kesin listesini değiştirir ve karşılaştırmayı yeniden yapar.",
      confirmLabel: "Yükle"
    });
    if (!ok) return;    setBusy(true);
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
    const ok = await confirm({
      title: "Karşılaştırmayı onayla",
      message: "Onaydan sonra birim dağıtımı yapılabilir. Bu işlem geri alınabilir.",
      confirmLabel: "Onayla"
    });
    if (!ok) return;    setBusy(true);
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

  async function gonderImzaBildirimi() {
    const ok = await confirm({
      title: "İmza bildirimi gönder",
      message: "Kesin kayıtlı öğrencilere sayfa bildirimi ve Proliz e-posta adresine e-posta gönderilir.",
      confirmLabel: "Gönder"
    });
    if (!ok) return;    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await api.gonderImzaBildirimi();
      await load();
      setMessage(`İmza bildirimi gönderildi: ${result.hedefOgrenci} öğrenci, ${result.epostaGonderilen} e-posta, ${result.epostaAtlanan} e-posta atlandı, ${result.epostaBasarisiz} e-posta başarısız.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "İmza bildirimi gönderilemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function geriAl() {
    const ok = await confirm({
      title: "Kesin listeyi sıfırla",
      message: "Kesin liste yüklemesi ve onayı geri alınır. Dağıtım yapılmamış olmalıdır.",
      confirmLabel: "Sıfırla",
      variant: "danger"
    });
    if (!ok) return;    setBusy(true);
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
      <PageHeader
        title="İŞKUR kesin kayıt"
        description="Onaylı başvuruları İŞKUR'a gönderin, dönen kesin listeyi yükleyin, imza bildirimi ve dağıtım adımlarını tamamlayın."
      />      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {data?.kesinOnaylandi && (
        <div className="alert alert-ok">
          Kesin liste karşılaştırması onaylandı{data.onaylayanAdmin ? ` (${data.onaylayanAdmin})` : ""}.
          {data.onayTarihi ? ` ${formatDate(data.onayTarihi)}` : ""}
        </div>
      )}
      {data?.imzaBildirimiGonderildi && (
        <div className="alert alert-ok">
          İmza bildirimi gönderildi{data.imzaBildirimiGonderenAdmin ? ` (${data.imzaBildirimiGonderenAdmin})` : ""}.
          {data.imzaBildirimiGonderimTarihi ? ` ${formatDate(data.imzaBildirimiGonderimTarihi)}` : ""}
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
        <h4 style={{ marginTop: 0 }}>2. İŞKUR&apos;dan gelen kesin listeyi yükleyin ve onaylayın</h4>
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
        <div className="row" style={{ marginTop: 14 }}>
          <button className="btn btn-gold" disabled={busy || !data?.kesinListeYuklendi || data?.kesinOnaylandi} onClick={() => void onayla()}>
            Karşılaştırmayı onayla
          </button>
          <button className="btn btn-danger" disabled={busy || (!data?.kesinListeYuklendi && !data?.kesinOnaylandi)} onClick={() => void geriAl()}>
            Listeyi sıfırla
          </button>
        </div>
      </section>      <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>3. Öğrencilere imza bildirimi gönderin</h4>
        <p style={{ color: "var(--muted)", lineHeight: 1.55, marginTop: 0 }}>
          Birim dağıtımından önce kesin kayıtlı öğrencilere hem sistemde mesaj hem de Proliz e-posta adresine bildirim gider.
          Merkez kampüs öğrencileri SKS Daire Başkanlığı&apos;na, taşra öğrencileri bulundukları birime gelerek imza atmalıdır.
        </p>
        <button
          className="btn btn-gold"
          disabled={busy || !data?.kesinOnaylandi || data?.imzaBildirimiGonderildi || (data?.kesinListede ?? 0) === 0}
          onClick={gonderImzaBildirimi}
        >
          İmza bildirimi gönder
        </button>
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
        <button className="btn btn-secondary" disabled={!data} onClick={() => window.print()}>
          Yazdır / PDF
        </button>
      </div>      {data && data.listedeEslesmeyenler.length > 0 && (
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
                <td colSpan={4}>
                  <EmptyState title="Bu filtrede öğrenci yok" description="Önce evrak onayını tamamlayın veya filtreyi değiştirin." />
                </td>
              </tr>
            )}          </tbody>
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
