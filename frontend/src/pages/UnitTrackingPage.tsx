import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, ApiError, authenticatedBlobUrl } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { Shell, formatDate } from "../components/ui";
import { PUANTAJ_LABEL, type PuantajDurum, type PuantajGun, type TakipDonem } from "../types";

const WEEKDAYS = ["Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"];
const MONTHS = [
  "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
  "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
];
const STATUSES: PuantajDurum[] = ["GELDI", "GELMEDI", "IZINLI", "RAPORLU"];
const DAYS_PER_WEEK = 3;

type CalCell = { date: string; inMonth: boolean; weekday: boolean; day: number };

function ymd(value: string) {
  return value.slice(0, 10);
}

function toYmd(date: Date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function mondayOf(dateStr: string) {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  const offset = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - offset);
  return toYmd(date);
}

function nextYearMonth(ym: string) {
  const [y, m] = ym.split("-").map(Number);
  const next = new Date(y, m, 1);
  return `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, "0")}`;
}

function buildMonth(yil: number, ay: number): CalCell[] {
  const first = new Date(yil, ay - 1, 1);
  const startPad = (first.getDay() + 6) % 7;
  const daysInMonth = new Date(yil, ay, 0).getDate();
  const cells: CalCell[] = [];
  for (let i = 0; i < startPad; i++) {
    cells.push({ date: "", inMonth: false, weekday: false, day: 0 });
  }
  for (let day = 1; day <= daysInMonth; day++) {
    const date = new Date(yil, ay - 1, day);
    const dow = date.getDay();
    cells.push({
      date: toYmd(date),
      inMonth: true,
      weekday: dow !== 0 && dow !== 6,
      day
    });
  }
  while (cells.length % 7 !== 0) {
    cells.push({ date: "", inMonth: false, weekday: false, day: 0 });
  }
  return cells;
}

function formatLong(dateStr: string) {
  const [y, m, d] = dateStr.split("-").map(Number);
  return `${d} ${MONTHS[m - 1]} ${y}`;
}

function needsDocument(durum: PuantajDurum | null | undefined) {
  return durum === "IZINLI" || durum === "RAPORLU";
}

export function UnitTrackingPage() {
  const confirm = useConfirm();
  const { basvuruId } = useParams();
  const id = Number(basvuruId);
  const now = new Date();
  const [yil, setYil] = useState(now.getFullYear());
  const [ay, setAy] = useState(now.getMonth() + 1);
  const [donem, setDonem] = useState<TakipDonem | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function load(nextYil = yil, nextAy = ay) {
    const data = await api.birimTakip(id, nextYil, nextAy);
    setDonem(data);
    return data;
  }

  useEffect(() => {
    if (!Number.isFinite(id)) return;
    load(yil, ay).catch((err) => setError(err instanceof ApiError ? err.message : "Takip yüklenemedi."));
  }, [id, yil, ay]);

  const cells = useMemo(() => buildMonth(yil, ay), [yil, ay]);
  const workDays = useMemo(() => new Set((donem?.ekuantGunler ?? []).map(ymd)), [donem]);
  const closedDays = useMemo(() => new Set((donem?.kapaliGunler ?? []).map(ymd)), [donem]);
  const puantajByDate = useMemo(() => {
    const map = new Map<string, PuantajGun>();
    for (const gun of donem?.puantaj ?? []) {
      map.set(ymd(gun.tarih), gun);
    }
    return map;
  }, [donem]);

  const selectedGun = selected ? puantajByDate.get(selected) : undefined;
  const locked = Boolean(donem?.locked);
  const quotaCount = donem?.ekuantKotaGunSayisi ?? 0;
  const quotaLimit = donem?.ekuantKotaGunLimiti ?? DAYS_PER_WEEK * 4;
  const currentWeekMonday = mondayOf(toYmd(now));
  const todayYmd = toYmd(now);
  const weekPlans = useMemo(() => {
    const byWeek = new Map<string, string[]>();
    cells.filter((cell) => cell.inMonth).forEach((cell) => {
      const key = mondayOf(cell.date);
      byWeek.set(key, [...(byWeek.get(key) ?? []), cell.date]);
    });
    const thisYm = `${yil}-${String(ay).padStart(2, "0")}`;
    const viewing = new Date(yil, ay - 1, 1);
    const nowMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    const monthOpen = viewing >= nowMonth;
    const entries = [...byWeek.entries()];
    const minima = entries.map(([, dates]) => {
      const countable = dates.filter((date) => {
        const day = Number(date.slice(8, 10));
        const quotaYm = day >= 29 ? nextYearMonth(date.slice(0, 7)) : date.slice(0, 7);
        if (quotaYm !== thisYm) return false;
        if (!monthOpen) return true;
        return date <= todayYmd;
      });
      return Math.min(DAYS_PER_WEEK, countable.length);
    });
    let sum = minima.reduce((total, value) => total + value, 0);
    const quotaLimitDays = DAYS_PER_WEEK * 4;
    for (let i = minima.length - 1; i >= 0 && sum > quotaLimitDays; i--) {
      const cut = Math.min(minima[i], sum - quotaLimitDays);
      minima[i] -= cut;
      sum -= cut;
    }
    return entries.map(([week, dates], index) => {
      const first = dates[0];
      const last = dates[dates.length - 1];
      return {
        week,
        label: `${Number(first.slice(8, 10))}–${Number(last.slice(8, 10))} ${MONTHS[ay - 1]}`,
        selected: dates.filter((date) => workDays.has(date)).length,
        required: minima[index],
        current: week === currentWeekMonday
      };
    });
  }, [cells, workDays, currentWeekMonday, ay, yil, now, todayYmd]);
  const puantajDayOpen = (date: string) => !locked && workDays.has(date) && !closedDays.has(date) && date <= todayYmd;
  const puantajEditable = selected ? puantajDayOpen(selected) : false;
  const izinLimitiDolu = (donem?.toplamIzinGunu ?? 0) >= (donem?.izinGunLimiti ?? 10);
  const kullanilanIzin = donem?.toplamIzinGunu ?? 0;
  const izinLimiti = donem?.izinGunLimiti ?? 10;
  const kalanIzin = Math.max(0, izinLimiti - kullanilanIzin);
  const missingPuantaj = [...workDays].filter((date) => date <= todayYmd && !puantajByDate.get(date)?.durum);
  const missingDocs = [...workDays].filter((date) => {
    const gun = puantajByDate.get(date);
    return date <= todayYmd && needsDocument(gun?.durum) && !gun?.belgeVar;
  });
  const futureWorkDays = [...workDays].filter((date) => date > todayYmd);
  const canSubmit = !locked
    && (donem?.ekuantUyarilari.length ?? 0) === 0
    && missingPuantaj.length === 0
    && missingDocs.length === 0
    && futureWorkDays.length === 0
    && workDays.size > 0;
  const incompleteWeeks = weekPlans.filter((week) => week.selected < week.required).length;
  const viewingOpen = new Date(yil, ay - 1, 1) >= new Date(now.getFullYear(), now.getMonth(), 1);
  const submissionSummary = canSubmit
    ? "Tüm kontroller tamamlandı. Ayı gönderime hazır."
    : [
        incompleteWeeks > 0 ? `${incompleteWeeks} haftanın EK-6 planı eksik` : "",
        !viewingOpen && quotaCount !== quotaLimit ? `aylık kota ${quotaCount}/${quotaLimit}` : "",
        missingPuantaj.length > 0 ? `${missingPuantaj.length} puantaj eksik` : "",
        futureWorkDays.length > 0 ? `${futureWorkDays.length} gelecek gün seçili` : "",
        missingDocs.length > 0 ? `${missingDocs.length} belge eksik` : ""
      ].filter(Boolean).join(" · ");

  function shiftMonth(delta: number) {
    const next = new Date(yil, ay - 1 + delta, 1);
    setYil(next.getFullYear());
    setAy(next.getMonth() + 1);
    setSelected(null);
    setError("");
    setMessage("");
  }

  async function run(action: () => Promise<TakipDonem>, okMessage?: string) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const data = await action();
      setDonem(data);
      if (okMessage) setMessage(okMessage);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "İşlem tamamlanamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function toggleEkuant(date: string) {
    if (locked || busy) return;
    if (closedDays.has(date)) {
      setError("Bu gün için EK-6 ve puantaj girişi yönetici tarafından kapatıldı.");
      return;
    }
    const next = new Set(workDays);
    if (next.has(date)) {
      next.delete(date);
      if (selected === date) setSelected(null);
    } else {
      const selectedInWeek = [...next].filter((item) => mondayOf(item) === mondayOf(date)).length;
      if (selectedInWeek >= DAYS_PER_WEEK) {
        setError(`Her hafta tam ${DAYS_PER_WEEK} EK-6 günü seçilmelidir; bu haftanın kotası dolu.`);
        return;
      }
      const dayOfMonth = Number(date.slice(8, 10));
      if (dayOfMonth < 29 && quotaCount >= quotaLimit) {
        setError(`Bu ayın 4 haftalık kotası dolu (${quotaLimit} gün).`);
        return;
      }
      next.add(date);
      setSelected(date);
    }
    await run(() => api.saveEkuant(id, yil, ay, [...next].sort()));
  }

  async function setDurum(durum: PuantajDurum) {
    if (!selected || busy || !puantajDayOpen(selected)) return;
    if (closedDays.has(selected)) {
      setError("Bu gün için EK-6 ve puantaj girişi yönetici tarafından kapatıldı.");
      return;
    }
    if (durum === "IZINLI" && selectedGun?.durum !== "IZINLI" && izinLimitiDolu) {
      setError(`İzin hakkı doldu. Bu öğrenci için yeni izin günü girilemez (${izinLimiti} gün).`);
      return;
    }
    await run(
      () => api.savePuantaj(id, yil, ay, [{ tarih: selected, durum }]),
      durum === "GELMEDI" ? "Öğrencinin ilişkisi ay sonunda kesilecek; sonraki ay birim listesinde görünmeyecek." : undefined
    );
  }

  async function onUpload(file: File) {
    if (!selected || !puantajDayOpen(selected)) return;
    await run(() => api.uploadPuantajBelge(id, yil, ay, selected, file), "Belge yüklendi.");
  }

  async function openBelge() {
    if (!selected || !donem) return;
    try {
      const url = await authenticatedBlobUrl(api.puantajBelgeUrl(id, donem.yil, donem.ay, selected));
      window.open(url, "_blank", "noopener");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Belge açılamadı.");
    }
  }

  async function submit() {
    if (!canSubmit) return;
    if (!await confirm({
      title: "Ayı gönder",
      message: `${MONTHS[ay - 1]} ${yil} EK-6 ve puantaj kaydı SKS yöneticisine gönderilecek. Gönderilen ay değiştirilemez.`,
      confirmLabel: "Gönder"
    })) {
      return;
    }
    await run(() => api.gonderTakip(id, yil, ay), "Ay gönderildi.");
  }

  return (
    <Shell home="/birim" wide>
      <div className="toolbar" style={{ alignItems: "center", justifyContent: "space-between" }}>
        <div>
          <Link to="/birim" className="back-link">← Öğrenci listesi</Link>
          <h3 className="section" style={{ margin: "8px 0 4px" }}>{donem?.ogrenci.adSoyad || "Öğrenci takibi"}</h3>
          <p style={{ color: "var(--muted)", margin: 0 }}>
            {donem?.ogrenci.ogrenciNo}
            {donem?.ogrenci.fakulte ? ` · ${donem.ogrenci.fakulte}` : ""}
          </p>
        </div>
        <div className="month-nav">
          <button type="button" className="btn btn-navy-ghost" onClick={() => shiftMonth(-1)} disabled={busy}>‹</button>
          <b>{MONTHS[ay - 1]} {yil}</b>
          <button type="button" className="btn btn-navy-ghost" onClick={() => shiftMonth(1)} disabled={busy}>›</button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}

      <section className="card help-tip" style={{ padding: 16, marginBottom: 18 }}>
        <b>Bu ay nasıl doldurulur?</b>
        <p style={{ margin: "6px 0 0", color: "var(--muted)", lineHeight: 1.55 }}>
          Haftada en fazla 3 gün EK-6 seçin. Ayın 29–31. günleri sonraki ayın 12 günlük kotasına sayılır.
          Puantaj yalnızca gelmiş günlere girilir; içinde bulunulan ay, bugüne kadarki haftalar tamamsa gönderilebilir.
        </p>
      </section>
      {locked && (
        <div className={`alert ${donem?.status === "APPROVED" ? "alert-ok" : "alert-wait"}`}>
          {donem?.status === "APPROVED"
            ? `Bu ay SKS tarafından onaylandı. Aylık rapor ekranından EK-6 ve puantaj cetvellerini Excel indirip veya yazdırıp imzalayabilirsiniz.`
            : `Bu ay ${formatDate(donem?.gonderimTarihi)} tarihinde gönderildi; yönetici onayı bekleniyor. Gönderilen kayıt değiştirilemez.`}
        </div>
      )}
      {izinLimitiDolu && (
        <div className="alert alert-error" role="alert">
          Bu öğrencinin izin hakkı doldu ({izinLimiti}/{izinLimiti} gün). Yeni izin günü işaretlenemez; raporlu gün girişi yapılabilir.
          Başka bir izin gününü iptal ederseniz bakiye açılır.
        </div>
      )}

      <section className={`leave-summary card ${izinLimitiDolu ? "exhausted" : ""}`} aria-label="Öğrenci izin bakiyesi">
        <div><span>Öğrenci izin bakiyesi</span><b>{izinLimitiDolu ? "Hak bitti" : `${kalanIzin} gün kaldı`}</b></div>
        <p>
          {izinLimitiDolu
            ? `Dönem boyunca ${izinLimiti} günlük izin kotası kullanıldı. Puantajda yeni “İzinli” seçilemez.`
            : `Toplam ${izinLimiti} günün ${kullanilanIzin} günü kullanıldı. Puantajda “İzinli” seçildiğinde bakiye otomatik düşer.`}
        </p>
      </section>

      <div className="cal-pair">
        <section className="card cal">
          <div className="cal-head">
            <div>
              <h4>EK-6</h4>
              <p>Her hafta tam {DAYS_PER_WEEK} gün seçilir; ay toplamı {quotaLimit} gündür. Ayın 29–31. günleri sonraki ayın kotasına sayılır.</p>
            </div>
            <span className={`badge ${donem?.status === "APPROVED" ? "APPROVED" : locked ? "SUBMITTED" : "DRAFT"}`}>
              {donem?.status === "APPROVED" ? "Onaylandı" : locked ? "Onay bekliyor" : "Taslak"}
            </span>
          </div>
          <CalendarGrid
            cells={cells}
            mode="ekuant"
            workDays={workDays}
            closedDays={closedDays}
            puantajByDate={puantajByDate}
            selected={selected}
            locked={locked || busy}
            quotaFull={quotaCount >= quotaLimit}
            onDay={(cell) => toggleEkuant(cell.date)}
          />
          <div className="schedule-progress" aria-label="EK-6 plan durumu">
            <div className="quota-progress">
              <span>Aylık kota</span><b>{quotaCount}/{quotaLimit} gün</b>
              <div className="occ" aria-hidden="true"><span style={{ width: `${Math.min(100, quotaLimit ? quotaCount / quotaLimit * 100 : 0)}%` }} /></div>
            </div>
            <div className="week-plan-list">
              {weekPlans.map((week) => (
                <div key={week.week} className={`week-plan ${week.selected === week.required ? "complete" : "incomplete"} ${week.current ? "current" : ""}`}>
                  <span>{week.current ? "Bu hafta · " : ""}{week.label}</span><b>{week.selected}/{week.required}</b>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="card cal">
          <div className="cal-head">
            <div>
              <h4>Puantaj</h4>
              <p>Yoklama ay içindeki EK-6 günlerine girilir; gelecek günler ay ilerledikçe açılır.</p>
            </div>
          </div>
          <CalendarGrid
            cells={cells}
            mode="puantaj"
            workDays={workDays}
            closedDays={closedDays}
            puantajByDate={puantajByDate}
            selected={selected}
            locked={locked || busy}
            todayYmd={todayYmd}
            onDay={(cell) => {
              if (!workDays.has(cell.date)) return;
              setError("");
              setMessage("");
              setSelected(cell.date);
              if (cell.date > todayYmd) {
                setError("Gelecek günler için puantaj girilemez.");
              }
            }}
          />
          <div className="cal-legend">
            {STATUSES.map((status) => (
              <span key={status} className={`legend ${status}`}>{PUANTAJ_LABEL[status]}</span>
            ))}
          </div>
        </section>
      </div>

      {selected && workDays.has(selected) && (
        <section className="card puantaj-panel">
          <div>
            <h4>{formatLong(selected)}</h4>
            <p>{closedDays.has(selected) ? "Bu gün için EK-6 ve puantaj girişi yönetici tarafından kapatıldı." : selected > todayYmd ? "Bu gün henüz gelmedi; puantaj ay ilerledikçe girilebilir." : puantajEditable ? (izinLimitiDolu ? `İzin kotası doldu; yeni “İzinli” seçilemez. Rapor veya geldi/gelmedi işaretleyebilirsiniz.` : `Bu gün için yoklama durumunu seçin. Kalan izin: ${kalanIzin}/${izinLimiti} gün.`) : "Bu ay gönderildiği için puantaj değiştirilemez."}</p>
          </div>
          <div className="status-row">
            {STATUSES.map((status) => (
              <button
                key={status}
                type="button"
                className={`status-chip ${status} ${selectedGun?.durum === status ? "active" : ""}`}
                disabled={!puantajEditable || busy || closedDays.has(selected) || (status === "IZINLI" && selectedGun?.durum !== "IZINLI" && izinLimitiDolu)}
                onClick={() => setDurum(status)}
              >
                {PUANTAJ_LABEL[status]}
              </button>
            ))}
          </div>
          {needsDocument(selectedGun?.durum) && (
            <div className="doc-upload">
              <label>{selectedGun?.durum === "RAPORLU" ? "Rapor belgesi" : "İzin dilekçesi"}</label>
              <input
                type="file"
                accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                disabled={!puantajEditable || busy || closedDays.has(selected)}
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  e.target.value = "";
                  if (file) onUpload(file);
                }}
              />
              {selectedGun?.belgeVar && (
                <div className="file-name" style={{ display: "flex", gap: 10, alignItems: "center" }}>
                  <span>{selectedGun.belgeAdi || "Belge yüklendi"}</span>
                  <button type="button" className="btn btn-primary" onClick={openBelge}>Görüntüle</button>
                </div>
              )}
            </div>
          )}
        </section>
      )}

      <section className={`submit-bar card ${canSubmit ? "ready" : ""}`} aria-live="polite">
        <div>
          <b>{canSubmit ? "Gönderime hazır" : "Gönderim için kalanlar"}</b>
          <span>{submissionSummary || "EK-6 günlerini seçerek başlayın."}</span>
        </div>
        <button className="btn btn-gold" disabled={!canSubmit || busy} onClick={submit}>
          {busy ? "Kaydediliyor..." : "Yöneticiye gönder"}
        </button>
      </section>
    </Shell>
  );
}

function CalendarGrid({
  cells,
  mode,
  workDays,
  closedDays,
  puantajByDate,
  selected,
  locked,
  quotaFull = false,
  todayYmd,
  onDay
}: {
  cells: CalCell[];
  mode: "ekuant" | "puantaj";
  workDays: Set<string>;
  closedDays: Set<string>;
  puantajByDate: Map<string, PuantajGun>;
  selected: string | null;
  locked: boolean;
  quotaFull?: boolean;
  todayYmd?: string;
  onDay: (cell: CalCell) => void;
}) {
  return (
    <div className="cal-grid">
      {WEEKDAYS.map((label) => (
        <div key={label} className="cal-dow">{label}</div>
      ))}
      {cells.map((cell, index) => {
        if (!cell.inMonth) {
          return <div key={`empty-${index}`} className="cal-day empty" />;
        }
        const isWork = workDays.has(cell.date);
        const isClosed = closedDays.has(cell.date);
        const gun = puantajByDate.get(cell.date);
        const monday = mondayOf(cell.date);
        const nextMonthQuota = cell.day >= 29;
        const quotaBlocked = quotaFull && !nextMonthQuota;
        const weekSelectedCount = [...workDays].filter((date) => mondayOf(date) === monday).length;
        const weekBlocked = mode === "ekuant" && !isWork && weekSelectedCount >= DAYS_PER_WEEK;
        const futurePuantajDay = mode === "puantaj" && isWork && todayYmd != null && cell.date > todayYmd;
        const classes = ["cal-day"];
        if (!cell.weekday) classes.push("weekend");
        if (mode === "ekuant" && isWork) classes.push("work");
        if (mode === "puantaj" && isWork) classes.push(gun?.durum || "pending");
        if (mode === "puantaj" && !isWork) classes.push("idle");
        if (mode === "ekuant" && !isWork && (quotaBlocked || weekBlocked)) classes.push("idle");
        if (futurePuantajDay) classes.push("closed");
        if (isClosed) classes.push("closed");
        if (selected === cell.date) classes.push("selected");
        const clickable = mode === "ekuant"
          ? !locked && !isClosed && (isWork || (!quotaBlocked && !weekBlocked))
          : isWork && !locked && !isClosed;
        const title = mode === "ekuant" && !isWork && (quotaBlocked || weekBlocked)
          ? quotaBlocked ? "Bu ayın 4 haftalık kotası dolu" : `Bu hafta ${DAYS_PER_WEEK} EK-6 günü seçildi`
          : isClosed
            ? "Bu gün için giriş yönetici tarafından kapatıldı"
            : futurePuantajDay
            ? "Gelecek günler için puantaj girilemez"
            : undefined;
        return (
          <button
            key={cell.date}
            type="button"
            className={classes.join(" ")}
            disabled={!clickable}
            title={title}
            onClick={() => onDay(cell)}
          >
            <span>{cell.day}</span>
            {mode === "puantaj" && gun?.durum && (
              <small>{PUANTAJ_LABEL[gun.durum]}</small>
            )}
          </button>
        );
      })}
    </div>
  );
}
