import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { FilterChips } from "../components/FilterChips";
import { SearchableSelect } from "../components/SearchableSelect";
import { ActionCard, PageHeader } from "../components/PageHeader";
import { AdminProcessNav } from "../components/AdminProcessNav";
import { OgrenciKimlikMeta, Shell } from "../components/ui";
import { EkuantSheet, MONTHS, PuantajSheet, calendarWeeks } from "./UnitReportPage";
import type { AdminTakipOzet, BasvuruDonemi, BirimAylikRapor, IzinRaporOgrenci, TakipGirisPencere, TakipStatus, WorkUnit } from "../types";

type PageTab = "paket" | "ozet" | "ayarlar" | "cetvel";
type CetvelKind = "ekuant" | "puantaj";

export function AdminTakipPage() {
  const confirm = useConfirm();
  const now = new Date();
  const [yil, setYil] = useState(now.getFullYear());
  const [ay, setAy] = useState(now.getMonth() + 1);
  const [birimKodlari, setBirimKodlari] = useState<string[]>([]);
  const [pageTab, setPageTab] = useState<PageTab>("ozet");
  const [cetvelKind, setCetvelKind] = useState<CetvelKind>("ekuant");
  const [units, setUnits] = useState<WorkUnit[]>([]);
  const [periods, setPeriods] = useState<BasvuruDonemi[]>([]);
  const [periodId, setPeriodId] = useState<number | undefined>();
  const [ozet, setOzet] = useState<AdminTakipOzet | null>(null);
  const [rapor, setRapor] = useState<BirimAylikRapor | null>(null);
  const [kapaliGunler, setKapaliGunler] = useState<string[]>([]);
  const [kapaliGunTarihi, setKapaliGunTarihi] = useState("");
  const [izinRaporlular, setIzinRaporlular] = useState<IzinRaporOgrenci[]>([]);
  const [kapaliOpen, setKapaliOpen] = useState(false);
  const [pencereOpen, setPencereOpen] = useState(true);
  const [izinOpen, setIzinOpen] = useState(false);
  const [pencere, setPencere] = useState<TakipGirisPencere | null>(null);
  const [ekuantBaslangic, setEkuantBaslangic] = useState("");
  const [ekuantBitis, setEkuantBitis] = useState("");
  const [puantajBaslangic, setPuantajBaslangic] = useState("");
  const [puantajBitis, setPuantajBitis] = useState("");
  const [donemEkuantBaslangic, setDonemEkuantBaslangic] = useState("");
  const [donemEkuantBitis, setDonemEkuantBitis] = useState("");
  const [donemPuantajBaslangic, setDonemPuantajBaslangic] = useState("");
  const [donemPuantajBitis, setDonemPuantajBitis] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const weeks = useMemo(() => calendarWeeks(yil, ay), [yil, ay]);
  const daysInMonth = new Date(yil, ay, 0).getDate();
  const needsRapor = pageTab === "cetvel";

  useEffect(() => {
    api.adminUnits().then(setUnits).catch(() => setUnits([]));
    api.basvuruDonemleri()
      .then((data) => {
        setPeriods(data);
        setPeriodId(data.find((period) => period.aktif)?.id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Dönemler yüklenemedi."));
  }, []);

  useEffect(() => {
    setLoading(true);
    setError("");
    const kod = birimKodlari.join(",") || undefined;
    const load = needsRapor
      ? api.adminTakipRapor(yil, ay, kod, periodId).then(setRapor)
      : api.adminTakip(yil, ay, kod, periodId).then(setOzet);
    load
      .catch((err) => setError(err instanceof ApiError ? err.message : "Takip bilgisi alınamadı."))
      .finally(() => setLoading(false));
  }, [yil, ay, birimKodlari, periodId, needsRapor]);

  useEffect(() => {
    api.adminKapaliGunler(yil, ay, periodId).then(setKapaliGunler).catch((err) =>
      setError(err instanceof ApiError ? err.message : "Kapalı günler yüklenemedi.")
    );
    api.adminGirisPencere(yil, ay, periodId)
      .then((next) => {
        applyPencere(next);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Giriş tarihleri yüklenemedi."));
    setKapaliGunTarihi(`${yil}-${String(ay).padStart(2, "0")}-01`);
  }, [yil, ay, periodId]);

  useEffect(() => {
    api.adminIzinRaporlular(yil, ay, birimKodlari.join(",") || undefined, periodId)
      .then(setIzinRaporlular)
      .catch((err) => setError(err instanceof ApiError ? err.message : "İzin/rapor listesi yüklenemedi."));
  }, [yil, ay, birimKodlari, periodId]);

  const selectedPeriod = periods.find((period) => period.id === periodId) ?? periods.find((period) => period.aktif);
  const monthPrefix = `${yil}-${String(ay).padStart(2, "0")}`;
  const lastDay = new Date(yil, ay, 0).getDate();
  const periodLabel = selectedPeriod?.ad ?? "Dönem seçin";
  const birimParam = birimKodlari.join(",") || undefined;
  const hasBirim = birimKodlari.length > 0;
  const birimLabel = !hasBirim
    ? "Tüm birimler"
    : birimKodlari.length === 1
      ? units.find((unit) => unit.kod === birimKodlari[0])?.ad ?? birimKodlari[0]
      : `${birimKodlari.length} birim`;
  const fileBirimSlug = !hasBirim
    ? "tum-birimler"
    : birimKodlari.length === 1
      ? birimKodlari[0]
      : `${birimKodlari.length}-birim`;
  const phaseSent = (status?: TakipStatus | null) => status === "SUBMITTED" || status === "APPROVED";
  const pendingApproval = (row: AdminTakipOzet["ogrenciler"][number]) =>
    row.ekuantStatus === "SUBMITTED" || row.puantajStatus === "SUBMITTED" || row.status === "SUBMITTED";
  const canReturn = (row: AdminTakipOzet["ogrenciler"][number]) =>
    phaseSent(row.ekuantStatus) || phaseSent(row.puantajStatus) || phaseSent(row.status);
  const approvedCount = ozet?.ogrenciler.filter((row) => (row.puantajStatus ?? row.status) === "APPROVED").length ?? 0;
  const submittedCount = ozet?.ogrenciler.filter((row) => canReturn(row)).length ?? 0;
  const ogrenciSayisi = pageTab === "cetvel" ? rapor?.ogrenciler.length ?? 0 : ozet?.ogrenciler.length ?? 0;

  async function saveClosedDays(next: string[]) {
    setLoading(true);
    setError("");
    setMessage("");
    try {
      const saved = await api.saveAdminKapaliGunler(yil, ay, next, periodId);
      setKapaliGunler(saved);
      setMessage("Kapalı gün ayarları kaydedildi.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Kapalı gün ayarları kaydedilemedi.");
    } finally {
      setLoading(false);
    }
  }

  function applyPencere(next: TakipGirisPencere) {
    setPencere(next);
    setEkuantBaslangic(next.ayEkuantBaslangic?.slice(0, 10) ?? "");
    setEkuantBitis(next.ayEkuantBitis?.slice(0, 10) ?? "");
    setPuantajBaslangic(next.ayPuantajBaslangic?.slice(0, 10) ?? "");
    setPuantajBitis(next.ayPuantajBitis?.slice(0, 10) ?? "");
    setDonemEkuantBaslangic(next.donemEkuantBaslangic?.slice(0, 10) ?? "");
    setDonemEkuantBitis(next.donemEkuantBitis?.slice(0, 10) ?? "");
    setDonemPuantajBaslangic(next.donemPuantajBaslangic?.slice(0, 10) ?? "");
    setDonemPuantajBitis(next.donemPuantajBitis?.slice(0, 10) ?? "");
  }

  async function saveGirisPencere(kapsam: "AYLIK" | "DONEMLIK") {
    setLoading(true);
    setError("");
    setMessage("");
    try {
      const saved = await api.saveAdminGirisPencere(yil, ay, kapsam === "DONEMLIK"
        ? {
            kapsam,
            ekuantBaslangic: donemEkuantBaslangic || null,
            ekuantBitis: donemEkuantBitis || null,
            puantajBaslangic: donemPuantajBaslangic || null,
            puantajBitis: donemPuantajBitis || null
          }
        : {
            kapsam,
            ekuantBaslangic: ekuantBaslangic || null,
            ekuantBitis: ekuantBitis || null,
            puantajBaslangic: puantajBaslangic || null,
            puantajBitis: puantajBitis || null
          }, periodId);
      applyPencere(saved);
      setMessage(kapsam === "DONEMLIK"
        ? "Dönemlik giriş tarihleri kaydedildi. Ay özel tarihi yoksa tüm aylarda bu aralık kullanılır."
        : `${MONTHS[ay - 1]} ${yil} giriş tarihleri kaydedildi. Bu ay dönemlik aralığın yerine geçer.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Giriş tarihleri kaydedilemedi.");
    } finally {
      setLoading(false);
    }
  }

  function addClosedDay() {
    if (!kapaliGunTarihi.startsWith(monthPrefix) || kapaliGunler.includes(kapaliGunTarihi)) return;
    void saveClosedDays([...kapaliGunler, kapaliGunTarihi].sort());
  }

  function downloadTerminatedStudents() {
    void downloadAuthenticatedFile(
      api.adminKesilenOgrencilerExcelUrl(birimParam, periodId),
      "iliskisi-kesilen-ogrenciler.xlsx"
    ).catch((err) => setError(err instanceof ApiError ? err.message : "Liste indirilemedi."));
  }

  function downloadLeaveReportList() {
    void downloadAuthenticatedFile(
      api.adminIzinRaporExcelUrl(yil, ay, birimParam, periodId),
      "izinli-raporlu-ogrenciler.xlsx"
    ).catch((err) => setError(err instanceof ApiError ? err.message : "Liste indirilemedi."));
  }

  async function approveStudent(basvuruId: number) {
    setLoading(true);
    setError("");
    setMessage("");
    try {
      await api.adminTakipOnayla(basvuruId, yil, ay, periodId);
      setMessage("Öğrenci kaydı onaylandı.");
      const next = await api.adminTakip(yil, ay, birimParam, periodId);
      setOzet(next);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Onay verilemedi.");
    } finally {
      setLoading(false);
    }
  }

  async function returnStudent(basvuruId: number, wasApproved: boolean) {
    const ok = await confirm({
      title: "Birime iade et",
      message: wasApproved
        ? "Onaylı kayıt taslağa alınır; birim düzeltip yeniden gönderir. Cetvel ve ödeme listesi bu öğrenci için güncellenene kadar geçersiz sayılır."
        : "Gönderilen kayıt birime iade edilir; birim EK-6 ve puantajı düzenleyip yeniden gönderebilir.",
      confirmLabel: "İade et",
      variant: "danger"
    });
    if (!ok) return;
    setLoading(true);
    setError("");
    setMessage("");
    try {
      await api.adminTakipIade(basvuruId, yil, ay, periodId);
      setMessage("Kayıt birime iade edildi.");
      const next = await api.adminTakip(yil, ay, birimParam, periodId);
      setOzet(next);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "İade edilemedi.");
    } finally {
      setLoading(false);
    }
  }

  async function returnSubmittedInUnit() {
    const ok = await confirm({
      title: hasBirim ? "Seçili birimlerdeki kayıtları iade et" : "Tüm birimlerdeki kayıtları iade et",
      message: hasBirim
        ? "Seçili birimlerde gönderilmiş ve onaylı tüm aylık kayıtlar taslağa alınır; birimler düzeltip yeniden gönderir."
        : "Tüm birimlerde gönderilmiş ve onaylı tüm aylık kayıtlar taslağa alınır; birimler düzeltip yeniden gönderir.",
      confirmLabel: "Toplu iade",
      variant: "danger"
    });
    if (!ok) return;
    setLoading(true);
    setError("");
    setMessage("");
    try {
      const count = await api.adminTakipIadeGonderilenler(yil, ay, birimParam, periodId);
      setMessage(`${count} öğrencinin kaydı birime iade edildi.`);
      const next = await api.adminTakip(yil, ay, birimParam, periodId);
      setOzet(next);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Toplu iade yapılamadı.");
    } finally {
      setLoading(false);
    }
  }

  async function approveSubmittedInUnit() {
    setLoading(true);
    setError("");
    setMessage("");
    try {
      const count = await api.adminTakipOnaylaGonderilenler(yil, ay, birimParam, periodId);
      setMessage(`${count} öğrencinin kaydı onaylandı.`);
      const next = await api.adminTakip(yil, ay, birimParam, periodId);
      setOzet(next);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Toplu onay verilemedi.");
    } finally {
      setLoading(false);
    }
  }

  function phaseLabel(status?: TakipStatus | null) {
    if (status === "APPROVED") return "Onaylandı";
    if (status === "SUBMITTED") return "Onay bekliyor";
    if (status === "DRAFT") return "Taslak";
    return "—";
  }

  function takipDurumLabel(row: AdminTakipOzet["ogrenciler"][number]) {
    return `EK-6 ${phaseLabel(row.ekuantStatus ?? row.status)} · Puantaj ${phaseLabel(row.puantajStatus)}`;
  }

  function downloadIskurPaketi() {
    const filename = `iskur-odeme_${fileBirimSlug}_${yil}-${String(ay).padStart(2, "0")}.xlsm`;
    void downloadAuthenticatedFile(
      api.adminIskurPaketiExcelUrl(yil, ay, birimParam, periodId),
      filename
    ).catch((err) => setError(err instanceof ApiError ? err.message : "İŞKUR paketi indirilemedi."));
  }

  function downloadTakipRaporExcel() {
    const filename = `ek6-puantaj_${fileBirimSlug}_${yil}-${String(ay).padStart(2, "0")}.xlsx`;
    void downloadAuthenticatedFile(
      api.adminTakipRaporExcelUrl(yil, ay, birimParam, periodId),
      filename
    ).catch((err) => setError(err instanceof ApiError ? err.message : "Excel indirilemedi."));
  }

  function downloadLeaveReportDocuments() {
    void downloadAuthenticatedFile(
      api.adminIzinRaporBelgeleriZipUrl(yil, ay, birimParam, periodId),
      "izin-rapor-dilekceleri.zip"
    ).catch((err) => setError(err instanceof ApiError ? err.message : "Dosyalar indirilemedi."));
  }

  return (
    <Shell home="/admin" full>
      <PageHeader
        title="Aylık devam ve ödeme"
        description="Birim önce EK-6’yı, onay sonrası ay sonunda puantajı gönderir. Her aşamayı ayrı onaylayın veya iade edin. Ödeme dosyası yalnızca onaylı puantajlarla alınır."
      />

      <div className="filter-bar no-print">
        <label className="filter-label">
          Dönem
          <select value={periodId ?? ""} onChange={(e) => setPeriodId(Number(e.target.value) || undefined)}>
            {periods.map((period) => <option key={period.id} value={period.id}>{period.ad}{period.aktif ? " (açık)" : " (arşiv)"}</option>)}
          </select>
        </label>
        <label className="filter-label">
          Ay
          <select value={ay} onChange={(e) => setAy(Number(e.target.value))}>
            {MONTHS.map((name, index) => (
              <option key={name} value={index + 1}>{name}</option>
            ))}
          </select>
        </label>
        <label className="filter-label">
          Yıl
          <select value={yil} onChange={(e) => setYil(Number(e.target.value))}>
            {[yil - 1, yil, yil + 1].map((year) => (
              <option key={year} value={year}>{year}</option>
            ))}
          </select>
        </label>
        <div className="filter-field">
          <span className="filter-label">Birim</span>
          <SearchableSelect
            multiple
            values={birimKodlari}
            onChange={setBirimKodlari}
            placeholder="Birim ara — kutucuklarla birden fazla seçin"
            emptyLabel="Eşleşen birim yok"
            options={units.map((unit) => ({ value: unit.kod, label: unit.ad, hint: `Kod ${unit.kod}` }))}
          />
        </div>
      </div>
      <AdminProcessNav />

      <FilterChips
        items={[
          selectedPeriod ? { label: selectedPeriod.ad, onClear: () => setPeriodId(periods.find((p) => p.aktif)?.id) } : { label: "" },
          { label: `${MONTHS[ay - 1]} ${yil}` },
          ...(hasBirim
            ? birimKodlari.map((kod) => ({
                label: units.find((unit) => unit.kod === kod)?.ad ?? kod,
                onClear: () => setBirimKodlari((current) => current.filter((item) => item !== kod))
              }))
            : [{ label: "Tüm birimler" }])
        ]}
      />

      <div className="page-tabs page-tabs-4 no-print">
        <button type="button" className={pageTab === "ozet" ? "active" : ""} onClick={() => setPageTab("ozet")}>Puantaj onayı</button>
        <button type="button" className={pageTab === "paket" ? "active" : ""} onClick={() => setPageTab("paket")}>Ödeme dosyası</button>
        <button type="button" className={pageTab === "cetvel" ? "active" : ""} onClick={() => setPageTab("cetvel")}>Cetveller</button>
        <button type="button" className={pageTab === "ayarlar" ? "active" : ""} onClick={() => setPageTab("ayarlar")}>Ayarlar</button>
      </div>

      {error && <div className="alert alert-error no-print">{error}</div>}
      {message && <div className="alert alert-ok no-print">{message}</div>}
      {loading && <div className="alert alert-wait no-print">Yükleniyor...</div>}

      {pageTab === "paket" && (
        <ActionCard
          title="İŞKUR ödeme dosyası"
          description="Yalnızca onaylı puantajlar yazılır. Dosya SKS-Onayli-Liste sekmesinde açılır; resmi KATILIMCI / Devamsızlık / Devam Gün Çizelgesi de doldurulur. Bordro boş kalır."
          meta={(
            <>
              <span className="meta-chip">{periodLabel}</span>
              <span className="meta-chip">{MONTHS[ay - 1]} {yil}</span>
              <span className="meta-chip">{birimLabel}</span>
              <span className="meta-chip">{ozet?.ogrenciler.filter((row) => (row.puantajStatus ?? row.status) === "APPROVED").length ?? 0} onaylı puantaj</span>
              <span className="meta-chip">{ogrenciSayisi} öğrenci</span>
            </>
          )}
        >
          <button
            className="btn btn-gold btn-lg"
            disabled={!selectedPeriod || loading || approvedCount === 0}
            onClick={() => downloadIskurPaketi()}
          >
            {hasBirim ? "Seçili birimlerin ödeme dosyasını indir" : "Tüm birimlerin ödeme dosyasını indir"}
          </button>
        </ActionCard>
      )}

      {pageTab === "ozet" && ozet && (
        <>
          <div className="secondary-actions no-print" style={{ marginBottom: 12 }}>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={loading || submittedCount === 0}
              onClick={downloadTakipRaporExcel}
            >
              EK-6 / puantaj Excel
            </button>
            <button
              type="button"
              className="btn btn-gold"
              disabled={loading || !ozet.ogrenciler.some(pendingApproval)}
              onClick={() => void approveSubmittedInUnit()}
            >
              {hasBirim ? "Seçili birimlerde onayla" : "Tüm birimlerde onayla"}
            </button>
            <button
              type="button"
              className="btn btn-danger"
              disabled={loading || !ozet.ogrenciler.some(canReturn)}
              onClick={() => void returnSubmittedInUnit()}
            >
              {hasBirim ? "Seçili birimlerde iade et" : "Tüm birimlerde iade et"}
            </button>
            <span style={{ color: "var(--muted)", fontSize: 13 }}>
              Birim seçilmezse tüm birimler; seçilirse yalnızca o birimler.
            </span>
          </div>
        <div className="card" style={{ overflow: "auto" }}>
          <table>
            <thead>
              <tr>
                <th>Öğrenci</th>
                <th>Birim</th>
                <th>EK-6</th>
                <th>Geldi</th>
                <th>Gelmedi</th>
                <th>İzinli</th>
                <th>Raporlu</th>
                <th>Durum</th>
                <th><span className="sr-only">İşlem</span></th>
              </tr>
            </thead>
            <tbody>
              {ozet.ogrenciler.map((row) => (
                <tr key={row.basvuruId}>
                  <td>
                    <b>{row.adSoyad}</b>
                    <OgrenciKimlikMeta ogrenciNo={row.ogrenciNo} tcKimlikNo={row.tcKimlikNo} />
                  </td>
                  <td>{row.birimAdi || "—"}</td>
                  <td>{row.ekuant}</td>
                  <td>{row.geldi}</td>
                  <td>{row.gelmedi}</td>
                  <td>{row.izinli}</td>
                  <td>{row.raporlu}</td>
                  <td>{takipDurumLabel(row)}</td>
                  <td className="no-wrap">
                    <button className="btn btn-secondary btn-compact" onClick={() => navigate(`/admin/takip/${row.basvuruId}?yil=${yil}&ay=${ay}${periodId ? `&donemId=${periodId}` : ""}`)}>İncele</button>
                    {pendingApproval(row) && (
                      <button className="btn btn-gold btn-compact" style={{ marginLeft: 6 }} disabled={loading} onClick={() => void approveStudent(row.basvuruId)}>
                        Onayla
                      </button>
                    )}
                    {canReturn(row) && (
                      <button
                        className="btn btn-danger btn-compact"
                        style={{ marginLeft: 6 }}
                        disabled={loading}
                        onClick={() => void returnStudent(row.basvuruId, (row.puantajStatus ?? row.status) === "APPROVED")}
                      >
                        İade
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {ozet.ogrenciler.length === 0 && (
                <tr>
                  <td colSpan={9} style={{ color: "var(--muted)" }}>
                    Dağıtılmış kesin kayıtlı öğrenci yok.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        </>
      )}

      {pageTab === "ayarlar" && (
        <>
          <section className="card collapsible-section no-print">
            <button type="button" className="collapsible-section-toggle" onClick={() => setPencereOpen((v) => !v)}>
              <span>EK-6 ve puantaj giriş tarihleri</span>
              <span className="collapsible-section-hint">{pencereOpen ? "Gizle" : "Göster"}</span>
            </button>
            {pencereOpen && (
              <div className="collapsible-section-body">
                <p style={{ color: "var(--muted)", marginTop: 0 }}>
                  Dönemlik aralık tüm aylara uygulanır. Aylık aralık yalnızca seçili ay için geçerlidir ve dönemliği ezer. Aralık dışında birim EK-6 / puantaj dolduramaz.
                </p>
                {selectedPeriod?.aktif ? (
                  <>
                    <h4 style={{ margin: "8px 0 6px" }}>Dönemlik ({selectedPeriod.ad})</h4>
                    <div className="row" style={{ alignItems: "end", flexWrap: "wrap", gap: 12 }}>
                      <label className="filter-label">
                        EK-6 başlangıç
                        <input type="date" value={donemEkuantBaslangic} onChange={(e) => setDonemEkuantBaslangic(e.target.value)} disabled={loading} />
                      </label>
                      <label className="filter-label">
                        EK-6 bitiş
                        <input type="date" value={donemEkuantBitis} onChange={(e) => setDonemEkuantBitis(e.target.value)} disabled={loading} />
                      </label>
                      <label className="filter-label">
                        Puantaj başlangıç
                        <input type="date" value={donemPuantajBaslangic} onChange={(e) => setDonemPuantajBaslangic(e.target.value)} disabled={loading} />
                      </label>
                      <label className="filter-label">
                        Puantaj bitiş
                        <input type="date" value={donemPuantajBitis} onChange={(e) => setDonemPuantajBitis(e.target.value)} disabled={loading} />
                      </label>
                      <button type="button" className="btn btn-gold" disabled={loading} onClick={() => void saveGirisPencere("DONEMLIK")}>
                        Dönemliği kaydet
                      </button>
                    </div>
                    <h4 style={{ margin: "18px 0 6px" }}>Aylık ({MONTHS[ay - 1]} {yil})</h4>
                    <div className="row" style={{ alignItems: "end", flexWrap: "wrap", gap: 12 }}>
                      <label className="filter-label">
                        EK-6 başlangıç
                        <input type="date" value={ekuantBaslangic} onChange={(e) => setEkuantBaslangic(e.target.value)} disabled={loading} />
                      </label>
                      <label className="filter-label">
                        EK-6 bitiş
                        <input type="date" value={ekuantBitis} onChange={(e) => setEkuantBitis(e.target.value)} disabled={loading} />
                      </label>
                      <label className="filter-label">
                        Puantaj başlangıç
                        <input type="date" value={puantajBaslangic} onChange={(e) => setPuantajBaslangic(e.target.value)} disabled={loading} />
                      </label>
                      <label className="filter-label">
                        Puantaj bitiş
                        <input type="date" value={puantajBitis} onChange={(e) => setPuantajBitis(e.target.value)} disabled={loading} />
                      </label>
                      <button type="button" className="btn btn-gold" disabled={loading} onClick={() => void saveGirisPencere("AYLIK")}>
                        Bu ayı kaydet
                      </button>
                    </div>
                    {pencere && (
                      <p style={{ color: "var(--muted)", marginBottom: 0, marginTop: 12 }}>
                        {MONTHS[ay - 1]} {yil} uygulanan aralık: EK-6 {pencere.ekuantAcik ? "açık" : "kapalı"}
                        {pencere.ekuantBaslangic && pencere.ekuantBitis ? ` (${pencere.ekuantBaslangic.slice(0, 10)} – ${pencere.ekuantBitis.slice(0, 10)}, ${pencere.ekuantKaynak === "AYLIK" ? "aylık" : pencere.ekuantKaynak === "DONEMLIK" ? "dönemlik" : "yok"})` : " · tarih yok"}
                        {" · "}Puantaj {pencere.puantajAcik ? "açık" : "kapalı"}
                        {pencere.puantajBaslangic && pencere.puantajBitis ? ` (${pencere.puantajBaslangic.slice(0, 10)} – ${pencere.puantajBitis.slice(0, 10)}, ${pencere.puantajKaynak === "AYLIK" ? "aylık" : pencere.puantajKaynak === "DONEMLIK" ? "dönemlik" : "yok"})` : " · tarih yok"}
                      </p>
                    )}
                  </>
                ) : (
                  <p style={{ color: "var(--muted)" }}>Kapalı dönemlerde giriş tarihleri değiştirilemez.</p>
                )}
              </div>
            )}
          </section>
          <section className="card collapsible-section no-print">
            <button type="button" className="collapsible-section-toggle" onClick={() => setKapaliOpen((v) => !v)}>
              <span>EK-6 ve puantaj girişine kapalı günler</span>
              <span className="collapsible-section-hint">{kapaliOpen ? "Gizle" : "Göster"}</span>
            </button>
            {kapaliOpen && (
              <div className="collapsible-section-body">
                <p style={{ color: "var(--muted)", marginTop: 0 }}>
                  Bu günlerde hiçbir birim EK-6 seçimi, puantaj durumu veya izin/rapor belgesi girişi yapamaz.
                </p>
                {selectedPeriod?.aktif ? (
                  <div className="row" style={{ alignItems: "center" }}>
                    <input
                      type="date"
                      value={kapaliGunTarihi}
                      min={`${monthPrefix}-01`}
                      max={`${monthPrefix}-${String(lastDay).padStart(2, "0")}`}
                      disabled={loading}
                      onChange={(event) => setKapaliGunTarihi(event.target.value)}
                      style={{ margin: 0, maxWidth: 190 }}
                    />
                    <button className="btn btn-danger" disabled={loading || !kapaliGunTarihi.startsWith(monthPrefix) || kapaliGunler.includes(kapaliGunTarihi)} onClick={addClosedDay}>
                      Günü kapat
                    </button>
                  </div>
                ) : (
                  <p style={{ color: "var(--muted)" }}>Kapalı dönemlerde gün ayarları değiştirilemez.</p>
                )}
                {kapaliGunler.length > 0 ? (
                  <div className="row" style={{ marginTop: 14, gap: 8 }}>
                    {kapaliGunler.map((date) => (
                      <button
                        type="button"
                        key={date}
                        className="btn btn-ghost"
                        disabled={loading || !selectedPeriod?.aktif}
                        title={selectedPeriod?.aktif ? "Günü yeniden girişe aç" : undefined}
                        onClick={() => void saveClosedDays(kapaliGunler.filter((item) => item !== date))}
                      >
                        {date} ×
                      </button>
                    ))}
                  </div>
                ) : <p style={{ color: "var(--muted)", marginBottom: 0 }}>Bu ay için kapalı gün tanımlanmadı.</p>}
              </div>
            )}
          </section>

          <section className="card collapsible-section no-print">
            <button type="button" className="collapsible-section-toggle" onClick={() => setIzinOpen((v) => !v)}>
              <span>İzinli ve raporlu öğrenciler</span>
              <span className="collapsible-section-hint">{izinOpen ? "Gizle" : "Göster"}</span>
            </button>
            {izinOpen && (
              <div className="collapsible-section-body">
                <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
                  <p style={{ color: "var(--muted)", margin: 0 }}>Seçili ay, dönem ve birim için izin/rapor günleri ile ekli belgeler.</p>
                  <div className="row">
                    <button className="btn btn-secondary" onClick={downloadLeaveReportList}>Liste (Excel)</button>
                    <button className="btn btn-secondary" onClick={downloadLeaveReportDocuments}>Belgeler (ZIP)</button>
                  </div>
                </div>
                {izinRaporlular.length === 0 ? <p style={{ color: "var(--muted)", marginBottom: 0 }}>Bu filtrede izinli veya raporlu öğrenci yok.</p> : (
                  <table style={{ marginTop: 14 }}>
                    <thead><tr><th>Öğrenci</th><th>Birim</th><th>Tarih</th><th>Durum</th><th>Belge</th></tr></thead>
                    <tbody>{izinRaporlular.map((row) => (
                      <tr key={`${row.basvuruId}-${row.tarih}-${row.durum}`}>
                        <td><b>{row.adSoyad}</b><OgrenciKimlikMeta ogrenciNo={row.ogrenciNo} tcKimlikNo={row.tcKimlikNo} /></td>
                        <td>{row.birimAdi || "—"}</td>
                        <td>{row.tarih}</td>
                        <td>{row.durum === "IZINLI" ? "İzinli" : "Raporlu"}</td>
                        <td>{row.belgeVar ? row.belgeAdi || "Belge yüklendi" : "Belge yok"}</td>
                      </tr>
                    ))}</tbody>
                  </table>
                )}
              </div>
            )}
          </section>
        </>
      )}

      {pageTab === "cetvel" && (
        <>
          <div className="secondary-actions no-print">
            <div className="tabs tabs-3" style={{ margin: 0, minWidth: 220 }}>
              <button type="button" className={cetvelKind === "ekuant" ? "active" : ""} onClick={() => setCetvelKind("ekuant")}>EK-6</button>
              <button type="button" className={cetvelKind === "puantaj" ? "active" : ""} onClick={() => setCetvelKind("puantaj")}>Puantaj</button>
            </div>
            <button className="btn btn-secondary" onClick={() => window.print()}>Cetveli yazdır / PDF</button>
            <button className="btn btn-secondary" disabled={!selectedPeriod} onClick={downloadTerminatedStudents}>
              İlişkisi kesilenler (Excel)
            </button>
          </div>
          {cetvelKind === "ekuant" && rapor && (
            <EkuantSheet yil={yil} ay={ay} birimAdi={rapor.birimAdi} weeks={weeks} ogrenciler={rapor.ogrenciler} />
          )}
          {cetvelKind === "puantaj" && rapor && (
            <PuantajSheet
              yil={yil}
              ay={ay}
              birimAdi={rapor.birimAdi}
              gunlukSaat={rapor.gunlukSaat}
              daysInMonth={daysInMonth}
              ogrenciler={rapor.ogrenciler}
            />
          )}
        </>
      )}
    </Shell>
  );
}
