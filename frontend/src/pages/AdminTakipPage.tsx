import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { Shell } from "../components/ui";
import { EkuantSheet, MONTHS, PuantajSheet, calendarWeeks } from "./UnitReportPage";
import type { AdminTakipOzet, BasvuruDonemi, BirimAylikRapor, IzinRaporOgrenci, WorkUnit } from "../types";

type Kind = "ozet" | "ekuant" | "puantaj";

export function AdminTakipPage() {
  const now = new Date();
  const [yil, setYil] = useState(now.getFullYear());
  const [ay, setAy] = useState(now.getMonth() + 1);
  const [birimKodu, setBirimKodu] = useState("");
  const [kind, setKind] = useState<Kind>("ozet");
  const [units, setUnits] = useState<WorkUnit[]>([]);
  const [periods, setPeriods] = useState<BasvuruDonemi[]>([]);
  const [periodId, setPeriodId] = useState<number | undefined>();
  const [ozet, setOzet] = useState<AdminTakipOzet | null>(null);
  const [rapor, setRapor] = useState<BirimAylikRapor | null>(null);
  const [kapaliGunler, setKapaliGunler] = useState<string[]>([]);
  const [kapaliGunTarihi, setKapaliGunTarihi] = useState("");
  const [izinRaporlular, setIzinRaporlular] = useState<IzinRaporOgrenci[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const weeks = useMemo(() => calendarWeeks(yil, ay), [yil, ay]);
  const daysInMonth = new Date(yil, ay, 0).getDate();

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
    const kod = birimKodu || undefined;
    const load = kind === "ozet"
      ? api.adminTakip(yil, ay, kod, periodId).then(setOzet)
      : api.adminTakipRapor(yil, ay, kod, periodId).then(setRapor);
    load
      .catch((err) => setError(err instanceof ApiError ? err.message : "Takip bilgisi alınamadı."))
      .finally(() => setLoading(false));
  }, [yil, ay, birimKodu, kind, periodId]);

  useEffect(() => {
    api.adminKapaliGunler(yil, ay, periodId).then(setKapaliGunler).catch((err) =>
      setError(err instanceof ApiError ? err.message : "Kapalı günler yüklenemedi.")
    );
    setKapaliGunTarihi(`${yil}-${String(ay).padStart(2, "0")}-01`);
  }, [yil, ay, periodId]);

  useEffect(() => {
    api.adminIzinRaporlular(yil, ay, birimKodu || undefined, periodId)
      .then(setIzinRaporlular)
      .catch((err) => setError(err instanceof ApiError ? err.message : "İzin/rapor listesi yüklenemedi."));
  }, [yil, ay, birimKodu, periodId]);

  const selectedPeriod = periods.find((period) => period.id === periodId) ?? periods.find((period) => period.aktif);
  const monthPrefix = `${yil}-${String(ay).padStart(2, "0")}`;
  const lastDay = new Date(yil, ay, 0).getDate();

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

  function addClosedDay() {
    if (!kapaliGunTarihi.startsWith(monthPrefix) || kapaliGunler.includes(kapaliGunTarihi)) return;
    void saveClosedDays([...kapaliGunler, kapaliGunTarihi].sort());
  }

  function downloadTerminatedStudents() {
    void downloadAuthenticatedFile(
      api.adminKesilenOgrencilerUrl(birimKodu || undefined, periodId),
      "iliskisi-kesilen-ogrenciler.csv"
    ).catch((err) => setError(err instanceof ApiError ? err.message : "Liste indirilemedi."));
  }

  function downloadLeaveReportList() {
    void downloadAuthenticatedFile(
      api.adminIzinRaporCsvUrl(yil, ay, birimKodu || undefined, periodId),
      "izinli-raporlu-ogrenciler.csv"
    ).catch((err) => setError(err instanceof ApiError ? err.message : "Liste indirilemedi."));
  }

  function downloadLeaveReportDocuments() {
    void downloadAuthenticatedFile(
      api.adminIzinRaporBelgeleriZipUrl(yil, ay, birimKodu || undefined, periodId),
      "izin-rapor-dilekceleri.zip"
    ).catch((err) => setError(err instanceof ApiError ? err.message : "Dosyalar indirilemedi."));
  }

  return (
    <Shell home="/admin" full>
      <div className="report-toolbar no-print">
        <div>
          <h3 className="section" style={{ margin: 0 }}>Öğrenci devam takibi</h3>
          <p style={{ color: "var(--muted)", margin: "6px 0 0" }}>
            Birim bazında veya tüm öğrenciler için EK-6 ve puantaj özeti. Cetvel yazdırılabilir.
          </p>
        </div>
        <div className="toolbar" style={{ margin: 0, flexWrap: "wrap" }}>
          <select value={periodId ?? ""} onChange={(e) => setPeriodId(Number(e.target.value) || undefined)} style={{ maxWidth: 260 }}>
            {periods.map((period) => <option key={period.id} value={period.id}>{period.ad}{period.aktif ? " (açık)" : " (arşiv)"}</option>)}
          </select>
          <select value={ay} onChange={(e) => setAy(Number(e.target.value))}>
            {MONTHS.map((name, index) => (
              <option key={name} value={index + 1}>{name}</option>
            ))}
          </select>
          <select value={yil} onChange={(e) => setYil(Number(e.target.value))}>
            {[yil - 1, yil, yil + 1].map((year) => (
              <option key={year} value={year}>{year}</option>
            ))}
          </select>
          <select value={birimKodu} onChange={(e) => setBirimKodu(e.target.value)} style={{ maxWidth: 280 }}>
            <option value="">Tüm birimler</option>
            {units.map((unit) => (
              <option key={unit.kod} value={unit.kod}>{unit.ad}</option>
            ))}
          </select>
          <div className="tabs tabs-3" style={{ margin: 0, minWidth: 280 }}>
            <button type="button" className={kind === "ozet" ? "active" : ""} onClick={() => setKind("ozet")}>Özet</button>
            <button type="button" className={kind === "ekuant" ? "active" : ""} onClick={() => setKind("ekuant")}>EK-6</button>
            <button type="button" className={kind === "puantaj" ? "active" : ""} onClick={() => setKind("puantaj")}>Puantaj</button>
          </div>
          {kind !== "ozet" && (
            <button className="btn btn-gold" onClick={() => window.print()}>Yazdır / PDF</button>
          )}
          <button className="btn btn-secondary" disabled={!selectedPeriod} onClick={downloadTerminatedStudents}>
            İlişkisi kesilenleri indir
          </button>
        </div>
      </div>
      {error && <div className="alert alert-error no-print">{error}</div>}
      {message && <div className="alert alert-ok no-print">{message}</div>}
      {loading && <div className="alert alert-wait no-print">Yükleniyor...</div>}

      <section className="card no-print" style={{ padding: 18, marginBottom: 18 }}>
        <h4 style={{ marginTop: 0 }}>EK-6 ve puantaj girişine kapalı günler</h4>
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
      </section>

      <section className="card no-print" style={{ padding: 18, marginBottom: 18, overflow: "auto" }}>
        <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <h4 style={{ margin: 0 }}>İzinli ve raporlu öğrenciler</h4>
            <p style={{ color: "var(--muted)", margin: "6px 0 0" }}>Seçili ay, dönem ve birim için izin/rapor günleri ile ekli belgeler.</p>
          </div>
          <div className="row">
            <button className="btn btn-secondary" onClick={downloadLeaveReportList}>Listeyi indir</button>
            <button className="btn btn-gold" onClick={downloadLeaveReportDocuments}>Tüm belge ve dilekçeleri indir</button>
          </div>
        </div>
        {izinRaporlular.length === 0 ? <p style={{ color: "var(--muted)", marginBottom: 0 }}>Bu filtrede izinli veya raporlu öğrenci yok.</p> : (
          <table style={{ marginTop: 14 }}>
            <thead><tr><th>Öğrenci</th><th>Birim</th><th>Tarih</th><th>Durum</th><th>Belge</th></tr></thead>
            <tbody>{izinRaporlular.map((row) => (
              <tr key={`${row.basvuruId}-${row.tarih}-${row.durum}`}>
                <td><b>{row.adSoyad}</b><div style={{ color: "var(--muted)" }}>{row.ogrenciNo}</div></td>
                <td>{row.birimAdi || "—"}</td>
                <td>{row.tarih}</td>
                <td>{row.durum === "IZINLI" ? "İzinli" : "Raporlu"}</td>
                <td>{row.belgeVar ? row.belgeAdi || "Belge yüklendi" : "Belge yok"}</td>
              </tr>
            ))}</tbody>
          </table>
        )}
      </section>

      {kind === "ozet" && ozet && (
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
                    <div style={{ color: "var(--muted)" }}>{row.ogrenciNo}</div>
                  </td>
                  <td>{row.birimAdi || "—"}</td>
                  <td>{row.ekuant}</td>
                  <td>{row.geldi}</td>
                  <td>{row.gelmedi}</td>
                  <td>{row.izinli}</td>
                  <td>{row.raporlu}</td>
                  <td>{row.locked ? "Gönderildi" : row.status === "DRAFT" ? "Taslak" : "—"}</td>
                  <td><button className="btn btn-secondary btn-compact" onClick={() => navigate(`/admin/takip/${row.basvuruId}?yil=${yil}&ay=${ay}${periodId ? `&donemId=${periodId}` : ""}`)}>İncele</button></td>
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
      )}

      {kind === "ekuant" && rapor && (
        <EkuantSheet yil={yil} ay={ay} birimAdi={rapor.birimAdi} weeks={weeks} ogrenciler={rapor.ogrenciler} />
      )}
      {kind === "puantaj" && rapor && (
        <PuantajSheet
          yil={yil}
          ay={ay}
          birimAdi={rapor.birimAdi}
          gunlukSaat={rapor.gunlukSaat}
          daysInMonth={daysInMonth}
          ogrenciler={rapor.ogrenciler}
        />
      )}
    </Shell>
  );
}
