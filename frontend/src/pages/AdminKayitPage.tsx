import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { EmptyState } from "../components/EmptyState";
import { PageHeader } from "../components/PageHeader";
import { AdminProcessNav } from "../components/AdminProcessNav";
import { Shell, formatDate } from "../components/ui";import { KayitListSheets } from "./AdminListReports";
import type { BasvuruDonemi, KayitListeFiltre, KayitListesi, SozlesmeImzaBekleyen } from "../types";

function nihaiDurumLabel(kesinListede: boolean | null) {
  if (kesinListede === true) return "İmza davetine alındı";
  if (kesinListede === false) return "Nihai listede yok";
  return "İŞKUR nihai listesi bekleniyor";
}

function matchesSearch(query: string, ...values: Array<string | null | undefined>) {
  const needle = query.trim().toLocaleLowerCase("tr-TR");
  if (!needle) return true;
  return values.some((value) => (value ?? "").toLocaleLowerCase("tr-TR").includes(needle));
}

export function AdminKayitPage() {
  const confirm = useConfirm();
  const [data, setData] = useState<KayitListesi | null>(null);
  const [imzaBekleyen, setImzaBekleyen] = useState<SozlesmeImzaBekleyen[]>([]);
  const [imzaSecili, setImzaSecili] = useState<Set<number>>(new Set());
  const [filter, setFilter] = useState<KayitListeFiltre>("TUMU");
  const [imzaArama, setImzaArama] = useState("");
  const [imzaFakulte, setImzaFakulte] = useState("");
  const [imzaBildirim, setImzaBildirim] = useState<"" | "OKUNDU" | "OKUNMADI">("");
  const [listeArama, setListeArama] = useState("");
  const [listeFakulte, setListeFakulte] = useState("");
  const [listeBirim, setListeBirim] = useState("");
  const [periods, setPeriods] = useState<BasvuruDonemi[]>([]);
  const [periodId, setPeriodId] = useState<number | undefined>();
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  // E-posta içeriği düzenleme modal state'i
  const [emailModalAcik, setEmailModalAcik] = useState(false);
  const [emailKonu, setEmailKonu] = useState("İŞKUR Nihai Liste – Sözleşme İmza Daveti");
  const [emailSablon, setEmailSablon] = useState(
    "Sayın {AD_SOYAD},\n\nİŞKUR nihai listesine alındınız. Birim ataması için {BIRIM} birimine gelerek sözleşme imzalamanız gerekmektedir."
  );

  const fileInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  async function load(nextFilter = filter) {
    const kayit = await api.kayitListesi(nextFilter, periodId);
    setData(kayit);
    if (kayit.imzaBildirimiGonderildi) {
      const bekleyen = await api.sozlesmeImzaBekleyen(periodId);
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
    api.basvuruDonemleri()
      .then((data) => {
        setPeriods(data);
        setPeriodId((current) => current ?? data.find((period) => period.aktif)?.id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Dönemler yüklenemedi."));
  }, []);

  useEffect(() => {
    if (!periodId) return;
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
  }, [periodId, filter]);

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

  async function gonderImzaBildirimi(konu: string, sablon: string) {
    const ok = await confirm({
      title: "Sözleşme daveti gönder",
      message: "İŞKUR nihai listesindeki öğrencilere sayfa bildirimi ve başvuruda girilen e-posta ile imza çağrısı gider.",
      confirmLabel: "Gönder"
    });
    if (!ok) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await api.gonderImzaBildirimi(konu, sablon);
      await load();
      setMessage(`İmza bildirimi gönderildi: ${result.hedefOgrenci} öğrenci, ${result.epostaGonderilen} e-posta, ${result.epostaAtlanan} e-posta atlandı, ${result.epostaBasarisiz} e-posta başarısız.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "İmza bildirimi gönderilemedi.");
    } finally {
      setBusy(false);
      setEmailModalAcik(false);
    }
  }

  async function geriAl() {
    const ok = await confirm({
      title: "Nihai listeyi sıfırla",
      message: "Yüklenen İŞKUR nihai listesi kaldırılır. İmza daveti ve dağıtım yapılmamış olmalıdır.",
      confirmLabel: "Sıfırla",
      variant: "danger"
    });
    if (!ok) return;
    setBusy(true);
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

  const donemAcik = periods.find((period) => period.id === periodId)?.aktif === true;
  const imzaFakulteler = useMemo(
    () => [...new Set(imzaBekleyen.map((row) => row.fakulte || row.program).filter(Boolean) as string[])].sort((a, b) => a.localeCompare(b, "tr")),
    [imzaBekleyen]
  );
  const gorunenImzaBekleyen = useMemo(
    () => imzaBekleyen.filter((row) => {
      if (imzaFakulte && (row.fakulte || row.program) !== imzaFakulte) return false;
      if (imzaBildirim === "OKUNDU" && !row.imzaBildirimiOkundu) return false;
      if (imzaBildirim === "OKUNMADI" && row.imzaBildirimiOkundu) return false;
      return matchesSearch(imzaArama, row.adSoyad, row.ogrenciNo, row.tcKimlikNo, row.fakulte, row.program);
    }),
    [imzaBekleyen, imzaArama, imzaFakulte, imzaBildirim]
  );
  const listeFakulteler = useMemo(
    () => [...new Set((data?.ogrenciler ?? []).map((row) => row.fakulte || row.program).filter(Boolean) as string[])].sort((a, b) => a.localeCompare(b, "tr")),
    [data]
  );
  const listeBirimler = useMemo(
    () => [...new Set((data?.ogrenciler ?? []).map((row) => row.atananBirimAdi).filter(Boolean) as string[])].sort((a, b) => a.localeCompare(b, "tr")),
    [data]
  );
  const gorunenListe = useMemo(
    () => (data?.ogrenciler ?? []).filter((row) => {
      if (listeFakulte && (row.fakulte || row.program) !== listeFakulte) return false;
      if (listeBirim && row.atananBirimAdi !== listeBirim) return false;
      return matchesSearch(listeArama, row.adSoyad, row.ogrenciNo, row.tcKimlikNo, row.fakulte, row.program, row.atananBirimAdi);
    }),
    [data, listeArama, listeFakulte, listeBirim]
  );

  return (
    <Shell home="/admin">
      <div className="no-print">
      <PageHeader
        title="Kesin kayıt ve sözleşme"
        description="Onaylı listeyi İŞKUR’a gönderin, nihai listeyi yükleyin, imza daveti yollayın. İmza gelenler birim dağıtımına girer."
      />
      <AdminProcessNav />
      <label className="filter-label" style={{ marginBottom: 12 }}>
        Dönem
        <select value={periodId ?? ""} onChange={(e) => setPeriodId(Number(e.target.value) || undefined)}>
          {periods.map((period) => <option key={period.id} value={period.id}>{period.ad}{period.aktif ? " (açık)" : " (arşiv)"}</option>)}
        </select>
      </label>
      {periods.find((period) => period.id === periodId)?.aktif === false && (
        <div className="alert alert-wait">Arşiv dönem yalnızca incelenir. Yükleme, imza ve onay işlemleri açık dönemdedir.</div>
      )}
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
      <div className="grid-3" style={{ marginBottom: 18 }}>
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
          onClick={() => downloadAuthenticatedFile(api.kayitListesiExcelUrl(periodId), "onayli-basvurular.xlsx")
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
            disabled={busy || !donemAcik || data?.imzaBildirimiGonderildi}
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
          <button className="btn btn-danger" disabled={busy || !donemAcik || !data?.kesinListeYuklendi || data?.imzaBildirimiGonderildi} onClick={() => void geriAl()}>
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
          disabled={busy || !donemAcik || !data?.kesinListeYuklendi || data?.imzaBildirimiGonderildi || (data?.kesinListede ?? 0) === 0}
          onClick={() => setEmailModalAcik(true)}
        >
          Davet e-postası gönder
        </button>
      </section>
      {emailModalAcik && (
        <ImzaEmailModal
          emailKonu={emailKonu}
          emailSablon={emailSablon}
          busy={busy}
          onKonuChange={setEmailKonu}
          onSablonChange={setEmailSablon}
          onGonder={() => void gonderImzaBildirimi(emailKonu, emailSablon)}
          onIptal={() => setEmailModalAcik(false)}
        />
      )}
      {data?.imzaBildirimiGonderildi && (
        <section className="card" style={{ padding: 18, marginBottom: 18, overflow: "auto" }}>
          <h4 style={{ marginTop: 0 }}>4. İmza gelenler kesin listeye alınır</h4>
          <p style={{ color: "var(--muted)", lineHeight: 1.55, marginTop: 0 }}>
            Gelip imza atanı &quot;İmza geldi&quot; ile işaretleyin. Gelmeyenleri pasife alın. Birim dağıtımına yalnızca imza gelenler girer.
          </p>
          <div className="row" style={{ marginBottom: 12, alignItems: "end" }}>
            <label className="filter-label filter-label-grow" style={{ margin: 0 }}>
              Ara
              <input
                placeholder="Ad, T.C. veya öğrenci no"
                value={imzaArama}
                onChange={(e) => setImzaArama(e.target.value)}
              />
            </label>
            <label className="filter-label" style={{ margin: 0 }}>
              Fakülte
              <select value={imzaFakulte} onChange={(e) => setImzaFakulte(e.target.value)}>
                <option value="">Tümü</option>
                {imzaFakulteler.map((name) => (
                  <option key={name} value={name}>{name}</option>
                ))}
              </select>
            </label>
            <label className="filter-label" style={{ margin: 0 }}>
              Bildirim
              <select value={imzaBildirim} onChange={(e) => setImzaBildirim(e.target.value as "" | "OKUNDU" | "OKUNMADI")}>
                <option value="">Tümü</option>
                <option value="OKUNMADI">Okunmadı</option>
                <option value="OKUNDU">Sayfada okundu</option>
              </select>
            </label>
          </div>
          <div className="row" style={{ marginBottom: 12, alignItems: "center" }}>
            <button
              type="button"
              className="btn btn-danger"
              disabled={busy || !donemAcik || imzaSecili.size === 0}
              onClick={() => void pasifToplu([...imzaSecili])}
            >
              Seçilenleri pasife al ({imzaSecili.size})
            </button>
            {gorunenImzaBekleyen.length > 0 && (
              <button
                type="button"
                className="btn btn-secondary"
                disabled={busy || !donemAcik}
                onClick={() => setImzaSecili(new Set(gorunenImzaBekleyen.map((r) => r.basvuruId)))}
              >
                Görünenleri seç
              </button>
            )}
            <span style={{ color: "var(--muted)" }}>
              {gorunenImzaBekleyen.length}/{imzaBekleyen.length} öğrenci imza bekliyor
            </span>
          </div>
          {imzaBekleyen.length === 0 ? (
            <p style={{ color: "var(--muted)", marginBottom: 0 }}>İmza bekleyen öğrenci yok (tümü işlendi veya pasife alındı).</p>
          ) : gorunenImzaBekleyen.length === 0 ? (
            <p style={{ color: "var(--muted)", marginBottom: 0 }}>Bu arama ve filtreye uyan öğrenci yok.</p>
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
                {gorunenImzaBekleyen.map((row) => (
                  <tr key={row.basvuruId}>
                    <td>
                      <input
                        type="checkbox"
                        checked={imzaSecili.has(row.basvuruId)}
                        disabled={busy || !donemAcik}
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
                        disabled={busy || !donemAcik}
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
      <div className="toolbar" style={{ flexWrap: "wrap", alignItems: "end" }}>
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
        <label className="filter-label filter-label-grow" style={{ margin: 0, minWidth: 220 }}>
          Ara
          <input
            placeholder="Ad, T.C. veya öğrenci no"
            value={listeArama}
            onChange={(e) => setListeArama(e.target.value)}
          />
        </label>
        <label className="filter-label" style={{ margin: 0 }}>
          Fakülte
          <select value={listeFakulte} onChange={(e) => setListeFakulte(e.target.value)}>
            <option value="">Tümü</option>
            {listeFakulteler.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </label>
        <label className="filter-label" style={{ margin: 0 }}>
          Birim
          <select value={listeBirim} onChange={(e) => setListeBirim(e.target.value)}>
            <option value="">Tümü</option>
            {listeBirimler.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </label>
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
            {gorunenListe.map((row) => (
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
            {gorunenListe.length === 0 && (
              <tr>
                <td colSpan={4}>
                  <EmptyState
                    title={(data?.ogrenciler.length ?? 0) === 0 ? "Bu filtrede öğrenci yok" : "Arama sonucu yok"}
                    description={(data?.ogrenciler.length ?? 0) === 0 ? "Önce evrak onayını tamamlayın veya liste filtresini değiştirin." : "Arama veya fakülte / birim filtresini değiştirin."}
                  />
                </td>
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

// ---------------------------------------------------------------------------
// E-posta içeriği düzenleme modal'ı
// ---------------------------------------------------------------------------

interface ImzaEmailModalProps {
  emailKonu: string;
  emailSablon: string;
  busy: boolean;
  onKonuChange: (val: string) => void;
  onSablonChange: (val: string) => void;
  onGonder: () => void;
  onIptal: () => void;
}

function ImzaEmailModal({
  emailKonu,
  emailSablon,
  busy,
  onKonuChange,
  onSablonChange,
  onGonder,
  onIptal,
}: ImzaEmailModalProps) {
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.45)",
        zIndex: 1000,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 16,
      }}
      onClick={(e) => { if (e.target === e.currentTarget) onIptal(); }}
    >
      <div
        style={{
          background: "var(--surface, #fff)",
          borderRadius: 12,
          padding: 28,
          width: "100%",
          maxWidth: 600,
          boxShadow: "0 8px 32px rgba(0,0,0,0.18)",
          display: "flex",
          flexDirection: "column",
          gap: 16,
        }}
      >
        <h3 style={{ margin: 0 }}>Davet e-postası içeriğini düzenle</h3>
        <p style={{ color: "var(--muted)", margin: 0, fontSize: 13, lineHeight: 1.5 }}>
          Aşağıdaki yer tutucular otomatik doldurulur:{" "}
          <code style={{ background: "var(--bg, #f5f5f5)", padding: "1px 5px", borderRadius: 4 }}>{"{AD_SOYAD}"}</code>{" "}
          öğrencinin adı soyadı,{" "}
          <code style={{ background: "var(--bg, #f5f5f5)", padding: "1px 5px", borderRadius: 4 }}>{"{BIRIM}"}</code>{" "}
          sözleşme imzalanacak birim adıdır. Boş bırakılırsa sistem varsayılan metni kullanır.
        </p>

        <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          <span style={{ fontWeight: 600, fontSize: 14 }}>E-posta konusu</span>
          <input
            type="text"
            value={emailKonu}
            onChange={(e) => onKonuChange(e.target.value)}
            placeholder="İŞKUR Nihai Liste – Sözleşme İmza Daveti"
            disabled={busy}
            style={{ padding: "8px 10px", borderRadius: 6, border: "1px solid var(--border, #ddd)", fontSize: 14 }}
          />
        </label>

        <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          <span style={{ fontWeight: 600, fontSize: 14 }}>E-posta metni</span>
          <textarea
            value={emailSablon}
            onChange={(e) => onSablonChange(e.target.value)}
            rows={8}
            disabled={busy}
            placeholder={
              "Sayın {AD_SOYAD},\n\nİŞKUR nihai listesine alındınız. Birim ataması için {BIRIM} birimine gelerek sözleşme imzalamanız gerekmektedir."
            }
            style={{
              padding: "8px 10px",
              borderRadius: 6,
              border: "1px solid var(--border, #ddd)",
              fontSize: 14,
              resize: "vertical",
              fontFamily: "inherit",
              lineHeight: 1.55,
            }}
          />
        </label>

        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 4 }}>
          <button
            type="button"
            className="btn btn-secondary"
            disabled={busy}
            onClick={onIptal}
          >
            İptal
          </button>
          <button
            type="button"
            className="btn btn-gold"
            disabled={busy}
            onClick={onGonder}
          >
            {busy ? "Gönderiliyor…" : "Gönder"}
          </button>
        </div>
      </div>
    </div>
  );
}
