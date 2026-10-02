import { useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { api, ApiError, authenticatedBlobUrl } from "../api";
import { Shell } from "../components/ui";
import { PUANTAJ_LABEL, type PuantajDurum, type PuantajGun, type TakipDonem } from "../types";

const WEEKDAYS = ["Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"];
const MONTHS = [
  "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
  "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
];

function toYmd(date: Date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function buildMonth(yil: number, ay: number) {
  const first = new Date(yil, ay - 1, 1);
  const startPad = (first.getDay() + 6) % 7;
  const daysInMonth = new Date(yil, ay, 0).getDate();
  const cells: { date: string; inMonth: boolean; day: number }[] = [];
  for (let i = 0; i < startPad; i++) cells.push({ date: "", inMonth: false, day: 0 });
  for (let day = 1; day <= daysInMonth; day++) {
    cells.push({ date: toYmd(new Date(yil, ay - 1, day)), inMonth: true, day });
  }
  while (cells.length % 7 !== 0) cells.push({ date: "", inMonth: false, day: 0 });
  return cells;
}

function formatLong(dateStr: string) {
  const [y, m, d] = dateStr.split("-").map(Number);
  return `${d} ${MONTHS[m - 1]} ${y}`;
}

function needsDocument(durum: PuantajDurum | null | undefined) {
  return durum === "IZINLI" || durum === "RAPORLU";
}

function belgeBaslik(durum: PuantajDurum | null | undefined) {
  if (durum === "RAPORLU") return "Rapor belgesi";
  if (durum === "IZINLI") return "İzin / mazeret dilekçesi";
  return "Belge";
}

export function AdminTakipDetail() {
  const { basvuruId } = useParams();
  const [params, setParams] = useSearchParams();
  const id = Number(basvuruId);
  const now = new Date();
  const yil = Number(params.get("yil") || now.getFullYear());
  const ay = Number(params.get("ay") || now.getMonth() + 1);
  const periodId = Number(params.get("donemId")) || undefined;
  const [donem, setDonem] = useState<TakipDonem | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [opening, setOpening] = useState(false);
  const cells = useMemo(() => buildMonth(yil, ay), [yil, ay]);

  useEffect(() => {
    if (!Number.isFinite(id)) return;
    setSelected(null);
    api.adminTakipOgrenci(id, yil, ay, periodId)
      .then(setDonem)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Takip yüklenemedi."));
  }, [id, yil, ay, periodId]);

  function shift(delta: number) {
    const next = new Date(yil, ay - 1 + delta, 1);
    const nextParams: Record<string, string> = { yil: String(next.getFullYear()), ay: String(next.getMonth() + 1) };
    if (periodId) nextParams.donemId = String(periodId);
    setParams(nextParams);
  }

  const work = new Set((donem?.ekuantGunler ?? []).map((d) => d.slice(0, 10)));
  const puantaj = useMemo(() => {
    const map = new Map<string, PuantajGun>();
    for (const gun of donem?.puantaj ?? []) {
      map.set(gun.tarih.slice(0, 10), gun);
    }
    return map;
  }, [donem]);
  const selectedGun = selected ? puantaj.get(selected) : undefined;

  async function openBelge(tarih: string) {
    if (!donem) return;
    setOpening(true);
    setError("");
    try {
      const url = await authenticatedBlobUrl(api.adminPuantajBelgeUrl(id, donem.yil, donem.ay, tarih));
      window.open(url, "_blank", "noopener");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Belge açılamadı.");
    } finally {
      setOpening(false);
    }
  }

  function onPuantajDay(date: string) {
    const gun = puantaj.get(date);
    if (!needsDocument(gun?.durum)) return;
    setSelected(date);
    if (gun?.belgeVar) {
      void openBelge(date);
    } else {
      setError("Bu gün izinli/raporlu işaretlenmiş ancak belge yüklenmemiş.");
    }
  }

  return (
    <Shell home="/admin">
      <Link to={`/admin/takip${periodId ? `?donemId=${periodId}` : ""}`} className="back-link">← Devam takibi</Link>
      <div className="toolbar" style={{ alignItems: "center", justifyContent: "space-between" }}>
        <h3 className="section" style={{ margin: 0 }}>{donem?.ogrenci.adSoyad || "Devam detayı"}</h3>
        <div className="month-nav">
          <button type="button" className="btn btn-navy-ghost" onClick={() => shift(-1)}>‹</button>
          <b>{MONTHS[ay - 1]} {yil}</b>
          <button type="button" className="btn btn-navy-ghost" onClick={() => shift(1)}>›</button>
        </div>
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      <p style={{ color: "var(--muted)" }}>
        {donem?.ogrenci.ogrenciNo}
        {donem?.ogrenci.tcKimlikNo ? ` · T.C. ${donem.ogrenci.tcKimlikNo}` : ""}
        {" · "}Bu ekran yalnız görüntülemedir; puantajı birim kullanıcısı doldurur.
        İzinli veya raporlu güne tıklayınca yüklenen dilekçe / raporu açabilirsiniz.
      </p>
      <div className="cal-pair">
        <section className="card cal">
          <div className="cal-head"><div><h4>EK-6</h4><p>Planlanan iş günleri.</p></div></div>
          <div className="cal-grid">
            {WEEKDAYS.map((d) => <div key={d} className="cal-dow">{d}</div>)}
            {cells.map((cell, index) => (
              <div key={cell.date || `e-${index}`} className={`cal-day ${!cell.inMonth ? "empty" : work.has(cell.date) ? "work" : "idle"}`}>
                {cell.inMonth ? cell.day : ""}
              </div>
            ))}
          </div>
        </section>
        <section className="card cal">
          <div className="cal-head"><div><h4>Puantaj</h4><p>İzinli / raporlu güne tıklayın, belgeyi görün.</p></div></div>
          <div className="cal-grid">
            {WEEKDAYS.map((d) => <div key={d} className="cal-dow">{d}</div>)}
            {cells.map((cell, index) => {
              const gun = puantaj.get(cell.date);
              const durum = gun?.durum;
              const clickable = Boolean(cell.inMonth && needsDocument(durum));
              const classes = [
                "cal-day",
                !cell.inMonth ? "empty" : durum || (work.has(cell.date) ? "pending" : "idle"),
                selected === cell.date ? "selected" : "",
                gun?.belgeVar ? "has-doc" : ""
              ].filter(Boolean).join(" ");
              if (!cell.inMonth) {
                return <div key={`p-${index}`} className="cal-day empty" />;
              }
              if (clickable) {
                return (
                  <button
                    key={cell.date}
                    type="button"
                    className={classes}
                    onClick={() => onPuantajDay(cell.date)}
                    title={gun?.belgeVar ? `${belgeBaslik(durum)} görüntüle` : "Belge yüklenmemiş"}
                  >
                    <span>{cell.day}</span>
                    {durum && <small>{PUANTAJ_LABEL[durum]}{gun?.belgeVar ? " · belge" : ""}</small>}
                  </button>
                );
              }
              return (
                <div key={cell.date} className={classes}>
                  <span>{cell.day}</span>
                  {durum && <small>{PUANTAJ_LABEL[durum]}</small>}
                </div>
              );
            })}
          </div>
        </section>
      </div>

      {selected && needsDocument(selectedGun?.durum) && (
        <section className="card puantaj-panel">
          <div>
            <h4>{formatLong(selected)}</h4>
            <p>{belgeBaslik(selectedGun?.durum)}</p>
          </div>
          {selectedGun?.belgeVar ? (
            <div className="file-name" style={{ display: "flex", gap: 10, alignItems: "center" }}>
              <span>{selectedGun.belgeAdi || "Belge yüklendi"}</span>
              <button type="button" className="btn btn-primary" disabled={opening} onClick={() => void openBelge(selected)}>
                {opening ? "Açılıyor..." : "Görüntüle"}
              </button>
            </div>
          ) : (
            <p style={{ color: "var(--muted)", margin: 0 }}>Bu gün için belge yüklenmemiş.</p>
          )}
        </section>
      )}
    </Shell>
  );
}
