import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { Shell, StatusBadge, formatDate } from "../components/ui";
import { ApplicationListSheet } from "./AdminListReports";
import { STATUS_LABEL, kesinListeDurumLabel, type AdminOzet, type ApplicationStatus, type Basvuru, type BasvuruDonemi } from "../types";

export function AdminDashboard() {
  const [ozet, setOzet] = useState<AdminOzet | null>(null);
  const [items, setItems] = useState<Basvuru[]>([]);
  const [status, setStatus] = useState<ApplicationStatus | "">("SUBMITTED");
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [periods, setPeriods] = useState<BasvuruDonemi[]>([]);
  const [periodId, setPeriodId] = useState<number | undefined>();
  const [onlyMine, setOnlyMine] = useState(false);
  const navigate = useNavigate();

  async function load(nextStatus = status, nextQuery = query, nextPeriodId = periodId, nextOnlyMine = onlyMine) {
    const [summary, list] = await Promise.all([
      api.adminSummary(nextPeriodId),
      api.adminList(nextStatus, nextQuery, nextPeriodId, nextOnlyMine)
    ]);
    setOzet(summary);
    setItems(list);
  }

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
  }, []);

  useEffect(() => {
    api.basvuruDonemleri()
      .then((data) => {
        setPeriods(data);
        setPeriodId(data.find((period) => period.aktif)?.id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Dönemler yüklenemedi."));
  }, []);

  const filterLabel = [status ? STATUS_LABEL[status] : "Tümü", query.trim() ? `Arama: ${query.trim()}` : ""]
    .filter(Boolean)
    .join(" · ");

  return (
    <Shell home="/admin">
      <div className="no-print">
      <h3 className="section">Başvuru takip ve raporlama</h3>
      <p style={{ color: "var(--muted)", marginTop: -10 }}>
        Tüm yöneticiler tüm başvuruları ve işlemleri görebilir; karar veren yönetici ayrıca kayda işlenir.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="grid-5" style={{ marginBottom: 18 }}>
        <Stat title="Toplam" value={ozet?.toplam} />
        <Stat title="Taslak" value={ozet?.taslak} />
        <Stat title="İncelemede" value={ozet?.gonderildi} />
        <Stat title="Onaylandı" value={ozet?.onaylandi} />
        <Stat title="Atanan" value={ozet?.atanan} />
      </div>
      <div className="toolbar">
        <select
          value={periodId ?? ""}
          onChange={(e) => {
            const next = Number(e.target.value) || undefined;
            setPeriodId(next);
            load(status, query, next).catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
          }}
        >
          {periods.map((period) => <option key={period.id} value={period.id}>{period.ad}{period.aktif ? " (açık)" : " (arşiv)"}</option>)}
        </select>
        <label style={{ display: "flex", alignItems: "center", gap: 6, whiteSpace: "nowrap" }}>
          <input
            type="checkbox"
            checked={onlyMine}
            onChange={(e) => {
              const next = e.target.checked;
              setOnlyMine(next);
              load(status, query, periodId, next).catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
            }}
            style={{ width: "auto", margin: 0 }}
          />
          Bana atananlar
        </label>
        <select
          value={status}
          onChange={(e) => {
            const value = e.target.value as ApplicationStatus | "";
            setStatus(value);
            load(value, query).catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
          }}
        >
          <option value="">Tümü</option>
          <option value="DRAFT">Taslak</option>
          <option value="SUBMITTED">İncelemede</option>
          <option value="RETURNED">İade edildi</option>
          <option value="APPROVED">Onaylandı</option>
          <option value="REJECTED">Reddedildi</option>
        </select>
        <label className="sr-only" htmlFor="application-search">Öğrenci veya başvuru ara</label>
        <input
          id="application-search"
          placeholder="Ad, soyad veya öğrenci no"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              load(status, query).catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
            }
          }}
        />
        <button className="btn btn-primary" onClick={() => load(status, query)}>Ara</button>
        <button
          className="btn btn-gold"
          disabled={items.length === 0}
          onClick={() => downloadAuthenticatedFile(
            api.adminBasvurularExcelUrl(status, query, periodId, onlyMine),
            "basvurular.xlsx"
          ).catch((err) => setError(err instanceof ApiError ? err.message : "Excel indirilemedi."))}
        >
          Excel indir
        </button>
        <button className="btn btn-secondary" onClick={() => window.print()} disabled={items.length === 0}>
          Yazdır / PDF
        </button>
      </div>
      <div className="card" style={{ overflow: "auto" }}>
        <table>
          <thead>
            <tr>
              <th>Öğrenci</th>
              <th>Fakülte</th>
              <th>Atanan birim</th>
              <th>İnceleme sorumlusu</th>
              <th>Durum</th>
              <th>Kesin liste</th>
              <th>Gönderim</th>
              <th>Belgeler</th>
              <th><span className="sr-only">İşlem</span></th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id}>
                <td>
                  <b>{item.student.adSoyad}</b>
                  <div style={{ color: "var(--muted)" }}>{item.student.ogrenciNo}</div>
                </td>
                <td>{item.student.fakulte || item.student.program || item.student.bolum}</td>
                <td>{item.atananBirimAdi || "—"}</td>
                <td>{item.atananAdmin || "Atama bekliyor"}</td>
                <td><StatusBadge status={item.status} /></td>
                <td>{kesinListeDurumLabel(item.kesinListede, item.status)}</td>
                <td>{formatDate(item.gonderimTarihi)}</td>
                <td>{item.belgeler.length}/5</td>
                <td><button className="btn btn-secondary btn-compact" onClick={() => navigate(`/admin/basvuru/${item.id}`)}>İncele</button></td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr>
                <td colSpan={9} style={{ color: "var(--muted)" }}>Kayıt bulunamadı.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      </div>
      <ApplicationListSheet items={items} filterLabel={filterLabel} />
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
