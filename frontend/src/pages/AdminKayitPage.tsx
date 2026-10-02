import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { EmptyState } from "../components/EmptyState";
import { PageHeader } from "../components/PageHeader";
import { Shell, formatDate } from "../components/ui";import { KayitListSheets } from "./AdminListReports";
import type { KayitListeFiltre, KayitListesi, SozlesmeImzaBekleyen } from "../types";

function nihaiDurumLabel(kesinListede: boolean | null) {
  if (kesinListede === true) return "İmza davetine alındı";
  if (kesinListede === false) return "Nihai listede yok";
  return "İŞKUR nihai listesi bekleniyor";
}

export function AdminKayitPage() {
  const confirm = useConfirm();
  const [data, setData] = useState<KayitListesi | null>(null);
  const [imzaBekleyen, setImzaBekleyen] = useState<SozlesmeImzaBekleyen[]>([]);
  const [imzaSecili, setImzaSecili] = useState<Set<number>>(new Set());
  const [filter, setFilter] = useState<KayitListeFiltre>("TUMU");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  async function load(nextFilter = filter) {
    const kayit = await api.kayitListesi(nextFilter);
    setData(kayit);
    if (kayit.imzaBildirimiGonderildi) {
      const bekleyen = await api.sozlesmeImzaBekleyen();
      setImzaBekleyen(bekleyen);
      setImzaSecili((prev) => {
        const ids = new Set(bekleyen.map((row) => row.basvuruId));
        return new Set([...prev].filter((id) => ids.has(id)));
      });
    } else {
      setImzaBekleyen([]);
      setImzaSecili(new Set());
    }
  }

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
  }, []);

  async function uploadKesinList(file: File) {
    const ok = await confirm({
      title: "İŞKUR nihai listesini yükle",
      message: "Listede adı geçen onaylı öğrenciler sözleşme imzasına davet edilir. İmza bildirimi gönderilmediyse dosya değiştirilebilir.",
      confirmLabel: "Yükle"
    });
    if (!ok) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await api.uploadKesinListe(file);
      await load();
      setMessage(`İŞKUR nihai listesi yüklendi. ${result.eslesen} öğrenci imza davetine alındı.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nihai liste yüklenemedi.");
    } finally {
      setBusy(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function gonderImzaBildirimi() {
    const ok = await confirm({
      title: "Sözleşme daveti gönder",
      message: "İŞKUR nihai listesindeki öğrencilere sayfa bildirimi ve başvuruda girilen e-posta ile imza çağrısı gider.",
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
      title: "Nihai listeyi sıfırla",
      message: "Yüklenen İŞKUR nihai listesi kaldırılır. İmza daveti ve dağıtım yapılmamış olmalıdır.",
      confirmLabel: "Sıfırla",
      variant: "danger"
    });
    if (!ok) return;    setBusy(true);
    setError("");
    setMessage("");
    try {
      const next = await api.geriAlKesinListe();
      setData(next);
      setMessage("Nihai liste sıfırlandı. Yeniden yükleme yapılabilir.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Onay geri alınamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function imzalandi(basvuruId: number) {
    setBusy(true);
    setError("");
    try {
      await api.sozlesmeImzalandi(basvuruId);
      await load();
      setMessage("Sözleşme imzası işlendi.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "İmza işlenemedi.");
    } finally {
      setBusy(false);
    }
  }

  async function pasifToplu(ids: number[]) {
    if (ids.length === 0) return;
    const ok = await confirm({
      title: "İmza gelmeyenleri pasife al",
      message: `${ids.length} öğrenci kesin kayıt ve birim dağıtım listesinden çıkarılacak; İŞKUR ilişkisi sonlandırılır.`,
      confirmLabel: "Pasife al",
      variant: "danger"
    });
    if (!ok) return;
    setBusy(true);
    setError("");
    try {
      const count = await api.sozlesmeImzaPasif(ids);
      await load();
      setMessage(`${count} öğrenci pasife alındı.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Pasife alma başarısız.");
    } finally {
      setBusy(false);
    }
  }

  function toggleImzaSecim(basvuruId: number) {
    setImzaSecili((prev) => {
      const next = new Set(prev);
      if (next.has(basvuruId)) next.delete(basvuruId);
      else next.add(basvuruId);
      return next;
    });
  }

  return (
    <Shell home="/admin">
      <div className="no-print">
      <PageHeader
        title="Kesin kayıt ve sözleşme"
        description="Onaylıları İŞKUR’a gönderin, dönen nihai listeyi yükleyin, imza daveti yollayın. İmza atanlar kesin listeye girer ve birimlere dağıtılır."
      />
      {data?.aktifDalga && (
        <p style={{ color: "var(--muted)", marginTop: -8 }}>
          Aktif başvuru turu: <b>{data.aktifDalga.ad}</b>
        </p>
      )}
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {data?.kesinListeYuklendi && (
        <div className="alert alert-ok">
          İŞKUR nihai listesi yüklendi{data.kesinListeYukleyenAdmin ? ` (${data.kesinListeYukleyenAdmin})` : ""}.
          {data.kesinListeYuklemeTarihi ? ` ${formatDate(data.kesinListeYuklemeTarihi)}` : ""}
        </div>
      )}
      {data?.imzaBildirimiGonderildi && (
        <div className="alert alert-ok">
          Sözleşme daveti gönderildi{data.imzaBildirimiGonderenAdmin ? ` (${data.imzaBildirimiGonderenAdmin})` : ""}.
          {data.imzaBildirimiGonderimTarihi ? ` ${formatDate(data.imzaBildirimiGonderimTarihi)}` : ""}
        </div>
      )}
      <div className="grid-5" style={{ marginBottom: 18 }}>
        <Stat title="Onaylı başvuru" value={data?.onayliBasvuru} />
        <Stat title="İmza daveti" value={data?.kesinListede} />
        <Stat title="İmza bekleyen" value={imzaBekleyen.length} />
      </div>
      <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>1. Onaylı başvuruları İŞKUR&apos;a gönderin</h4>
        <p style={{ color: "var(--muted)", lineHeight: 1.55, marginTop: 0 }}>
          Yönetici onayından geçen öğrencilerin listesini indirip İŞKUR&apos;a iletin.
        </p>
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
        <h4 style={{ marginTop: 0 }}>2. İŞKUR nihai listesini yükleyin</h4>
        <p style={{ color: "var(--muted)", lineHeight: 1.55, marginTop: 0 }}>
          İŞKUR incelemesinden dönen listeyi yükleyin. Adı geçen onaylı öğrenciler sözleşme imzasına davet edilir; ayrı bir karşılaştırma onayı yoktur.
        </p>
        <div className="row" style={{ alignItems: "center" }}>
          <input
            ref={fileInputRef}
            type="file"
            accept=".xlsx,.xls"
            disabled={busy || data?.imzaBildirimiGonderildi}
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
          <button className="btn btn-danger" disabled={busy || !data?.kesinListeYuklendi || data?.imzaBildirimiGonderildi} onClick={() => void geriAl()}>
            Listeyi sıfırla
          </button>
        </div>
      </section>
      <section className="card" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>3. Sözleşme imza daveti gönderin</h4>
        <p style={{ color: "var(--muted)", lineHeight: 1.55, marginTop: 0 }}>
          Nihai listedeki öğrencilere e-posta ve sayfa bildirimi gider. Merkez kampüs SKS&apos;ye, taşra öğrencileri bulundukları birime gelerek sözleşme imzalar.
        </p>
        <button
          className="btn btn-gold"
          disabled={busy || !data?.kesinListeYuklendi || data?.imzaBildirimiGonderildi || (data?.kesinListede ?? 0) === 0}
          onClick={gonderImzaBildirimi}
        >
          Davet e-postası gönder
        </button>
      </section>
      {data?.imzaBildirimiGonderildi && (
        <section className="card" style={{ padding: 18, marginBottom: 18, overflow: "auto" }}>
          <h4 style={{ marginTop: 0 }}>4. İmza gelenler kesin listeye alınır</h4>
          <p style={{ color: "var(--muted)", lineHeight: 1.55, marginTop: 0 }}>
            Gelip imza atanı &quot;İmza geldi&quot; ile işaretleyin. Gelmeyenleri pasife alın. Birim dağıtımına yalnızca imza gelenler girer.
          </p>
          <div className="row" style={{ marginBottom: 12, alignItems: "center" }}>
            <button
              type="button"
              className="btn btn-danger"
              disabled={busy || imzaSecili.size === 0}
              onClick={() => void pasifToplu([...imzaSecili])}
            >
              Seçilenleri pasife al ({imzaSecili.size})
            </button>
            {imzaBekleyen.length > 0 && (
              <button
                type="button"
                className="btn btn-secondary"
                disabled={busy}
                onClick={() => setImzaSecili(new Set(imzaBekleyen.map((r) => r.basvuruId)))}
              >
                Tümünü seç
              </button>
            )}
            <span style={{ color: "var(--muted)" }}>{imzaBekleyen.length} öğrenci imza bekliyor</span>
          </div>
          {imzaBekleyen.length === 0 ? (
            <p style={{ color: "var(--muted)", marginBottom: 0 }}>İmza bekleyen öğrenci yok (tümü işlendi veya pasife alındı).</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th />
                  <th>Öğrenci</th>
                  <th>Fakülte</th>
                  <th>Bildirim</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {imzaBekleyen.map((row) => (
                  <tr key={row.basvuruId}>
                    <td>
                      <input
                        type="checkbox"
                        checked={imzaSecili.has(row.basvuruId)}
                        disabled={busy}
                        onChange={() => toggleImzaSecim(row.basvuruId)}
                        aria-label={`${row.adSoyad} seç`}
                      />
                    </td>
                    <td>
                      <b>{row.adSoyad}</b>
                      <div style={{ color: "var(--muted)", fontSize: 13 }}>
                        <div>{row.ogrenciNo}</div>
                        <div>T.C. {row.tcKimlikNo || "—"}</div>
                      </div>
                    </td>
                    <td>{row.fakulte || row.program || "—"}</td>
                    <td style={{ fontSize: 13 }}>
                      {row.imzaBildirimiOkundu ? "Sayfada okundu" : "Okunmadı"}
                      {row.imzaBildirimiEpostaGonderildi ? " · e-posta" : ""}
                      {row.imzaBildirimiGonderimTarihi ? ` · ${formatDate(row.imzaBildirimiGonderimTarihi)}` : ""}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="btn btn-gold btn-compact"
                        disabled={busy}
                        onClick={() => void imzalandi(row.basvuruId)}
                      >
                        İmza geldi
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}
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
          <option value="KESIN_LISTEDE">İmza davetine alınanlar</option>
          <option value="KESIN_LISTEDE_DEGIL">Nihai listede olmayanlar</option>
          <option value="ONAYLI_BASVURU">Nihai liste bekleyen</option>
        </select>
        <button className="btn btn-secondary" disabled={!data} onClick={() => window.print()}>
          Yazdır / PDF
        </button>
      </div>
      <div className="card" style={{ overflow: "auto" }}>
        <table>
          <thead>
            <tr>
              <th>Öğrenci</th>
              <th>Fakülte</th>
              <th>Durum</th>
              <th>Birim</th>
            </tr>
          </thead>
          <tbody>
            {data?.ogrenciler.map((row) => (
              <tr key={row.basvuruId}>
                <td className="clickable" onClick={() => navigate(`/admin/basvuru/${row.basvuruId}`)}>
                  <b>{row.adSoyad}</b>
                  <div style={{ color: "var(--muted)", fontSize: 13 }}>
                    <div>{row.ogrenciNo}</div>
                    <div>T.C. {row.tcKimlikNo || "—"}</div>
                  </div>
                </td>
                <td className="clickable" onClick={() => navigate(`/admin/basvuru/${row.basvuruId}`)}>
                  {row.fakulte || row.program || "—"}
                </td>
                <td>{nihaiDurumLabel(row.kesinListede)}</td>
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
