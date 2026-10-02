import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api, ApiError, downloadAuthenticatedFile } from "../api";
import { useAuth } from "../auth";
import { useConfirm } from "../components/ConfirmDialog";
import { Shell } from "../components/ui";
import type { BirimAylikRapor, BirimRaporOgrenci, TakipStatus } from "../types";

export const MONTHS = [
  "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
  "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
];
const WEEKDAYS = ["Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"];

type Kind = "ekuant" | "puantaj";

function toYmd(date: Date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function ymd(value: string) {
  return value.slice(0, 10);
}

export function calendarWeeks(yil: number, ay: number): (string | null)[][] {
  const first = new Date(yil, ay - 1, 1);
  const last = new Date(yil, ay, 0);
  const monday = new Date(first);
  monday.setDate(first.getDate() - ((first.getDay() + 6) % 7));
  const weeks: (string | null)[][] = [];
  const cursor = new Date(monday);
  while (cursor <= last) {
    const week: (string | null)[] = [];
    for (let i = 0; i < 7; i++) {
      week.push(cursor.getMonth() === ay - 1 ? toYmd(cursor) : null);
      cursor.setDate(cursor.getDate() + 1);
    }
    weeks.push(week);
  }
  return weeks;
}

function upper(value?: string | null) {
  return (value || "").toLocaleUpperCase("tr-TR");
}

function formatSaat(value: number) {
  return value.toLocaleString("tr-TR", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

function statusLabel(status?: TakipStatus | null) {
  if (status === "APPROVED") return "Onaylandı";
  if (status === "SUBMITTED") return "Onay bekliyor";
  if (status === "DRAFT") return "Taslak";
  return "Eksik";
}

export function UnitReportPage() {
  const confirm = useConfirm();
  const { session } = useAuth();
  const [searchParams] = useSearchParams();
  const [yearOptions] = useState(() => {
    const current = new Date().getFullYear();
    return [current - 1, current, current + 1];
  });
  const now = new Date();
  const [yil, setYil] = useState(() => Number(searchParams.get("yil")) || now.getFullYear());
  const [ay, setAy] = useState(() => Number(searchParams.get("ay")) || now.getMonth() + 1);
  const [kind, setKind] = useState<Kind>("ekuant");
  const [data, setData] = useState<BirimAylikRapor | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  async function reload() {
    setLoading(true);
    setError("");
    setMessage("");
    try {
      setData(await api.birimRapor(yil, ay));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Rapor alınamadı.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void reload();
  }, [yil, ay]);

  const birimAdi = data?.birimAdi || session?.birimAdi || session?.displayName || "";
  const gunlukSaat = data?.gunlukSaat ?? 7.5;
  const ogrenciler = data?.ogrenciler ?? [];
  const weeks = useMemo(() => calendarWeeks(yil, ay), [yil, ay]);
  const daysInMonth = new Date(yil, ay, 0).getDate();

  function printReport() {
    if (!data?.yazdirilabilir) return;
    const title = kind === "ekuant" ? "EK-6" : "Puantaj";
    const previous = document.title;
    document.title = `${title} ${MONTHS[ay - 1]} ${yil} - ${birimAdi}`;
    window.print();
    document.title = previous;
  }

  function downloadExcel() {
    if (!data?.yazdirilabilir) return;
    void downloadAuthenticatedFile(
      api.birimRaporExcelUrl(yil, ay),
      `ek6-puantaj_${yil}-${String(ay).padStart(2, "0")}.xlsx`
    ).catch((err) => setError(err instanceof ApiError ? err.message : "Excel indirilemedi."));
  }

  async function submitMonth() {
    if (!data?.gonderilebilir) return;
    if (!await confirm({
      title: "Ayı SKS'ye gönder",
      message: `${MONTHS[ay - 1]} ${yil} için tüm öğrencilerin EK-6 ve puantaj kaydı birlikte gönderilecek. Gönderilen ay birim tarafından değiştirilemez.`,
      confirmLabel: "Gönder"
    })) {
      return;
    }
    setLoading(true);
    setError("");
    setMessage("");
    try {
      const result = await api.gonderBirimTakip(yil, ay);
      setMessage(`${result.gonderilenOgrenci} öğrencinin kaydı gönderildi. SKS onayı bekleniyor.`);
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gönderim tamamlanamadı.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Shell home="/birim" full>
      <div className="report-toolbar no-print">
        <div>
          <h3 className="section" style={{ margin: 0 }}>Aylık EK-6 / puantaj raporu</h3>
          <p style={{ color: "var(--muted)", margin: "6px 0 0" }}>
            Önce listedeki her öğrenci için EK-6 ve puantajı tamamlayın; ardından bu ekrandan ayı tek seferde SKS’ye gönderin. Onay sonrası cetveli Excel veya yazdırı ile indirin.
          </p>
        </div>
        <div className="toolbar" style={{ margin: 0 }}>
          <select value={ay} onChange={(e) => setAy(Number(e.target.value))}>
            {MONTHS.map((name, index) => (
              <option key={name} value={index + 1}>{name}</option>
            ))}
          </select>
          <select value={yil} onChange={(e) => setYil(Number(e.target.value))}>
            {yearOptions.map((year) => (
              <option key={year} value={year}>{year}</option>
            ))}
          </select>
          <div className="tabs" style={{ margin: 0, minWidth: 220 }}>
            <button type="button" className={kind === "ekuant" ? "active" : ""} onClick={() => setKind("ekuant")}>
              EK-6
            </button>
            <button type="button" className={kind === "puantaj" ? "active" : ""} onClick={() => setKind("puantaj")}>
              Puantaj
            </button>
          </div>
          <button type="button" className="btn btn-secondary" onClick={downloadExcel} disabled={loading || !data?.yazdirilabilir}>
            Excel indir
          </button>
          <button type="button" className="btn btn-gold" onClick={printReport} disabled={loading || !data?.yazdirilabilir}>
            Yazdır / PDF
          </button>
          <button type="button" className="btn btn-gold" onClick={() => void submitMonth()} disabled={loading || !data?.gonderilebilir}>
            Yöneticiye gönder
          </button>
        </div>
      </div>
      {message && <div className="alert alert-ok no-print">{message}</div>}
      {data && !data.yazdirilabilir && (
        <div className="alert alert-wait no-print">
          {data.onayBekleyenOgrenci > 0
            ? `${data.onayBekleyenOgrenci} öğrencinin kaydı yönetici onayı bekliyor.`
            : data.tamamlanmamisOgrenci > 0
              ? `${data.tamamlanmamisOgrenci} öğrencinin EK-6 veya puantajı eksik. Öğrenci satırından tamamlayın.`
              : data.gonderilebilir
                ? "Tüm öğrenciler hazır; «Yöneticiye gönder» ile ayı iletin."
                : data.gonderilmeyenOgrenci > 0
                  ? `${data.gonderilmeyenOgrenci} öğrencinin ayı henüz gönderilmedi.`
                  : "Bu ay için yazdırma henüz açılmadı."}
          {data.tamamlanmamisOgrenci > 0 && (
            <span> <Link to="/birim">Öğrenci listesi</Link> üzerinden eksikleri tamamlayın.</span>
          )}
        </div>
      )}
      {data?.yazdirilabilir && (
        <div className="alert alert-ok no-print">
          {MONTHS[ay - 1]} {yil} cetvelleri onaylandı. Yazdırıp birim imzası için hazırlayabilirsiniz.
        </div>
      )}
      {error && <div className="alert alert-error no-print">{error}</div>}
      {loading && <div className="alert alert-wait no-print">Rapor hazırlanıyor...</div>}
      {data && data.ogrenciler.length > 0 && (
        <div className="card no-print" style={{ overflow: "auto", marginBottom: 16 }}>
          <table>
            <thead>
              <tr>
                <th>Öğrenci</th>
                <th>EK-6 gün</th>
                <th>Puantaj</th>
                <th>Durum</th>
              </tr>
            </thead>
            <tbody>
              {data.ogrenciler.map((ogrenci) => (
                <tr key={ogrenci.basvuruId}>
                  <td>
                    <Link to={`/birim/ogrenci/${ogrenci.basvuruId}?yil=${yil}&ay=${ay}`}>
                      <b>{ogrenci.ad} {ogrenci.soyad}</b>
                    </Link>
                    <div style={{ color: "var(--muted)", fontSize: 12 }}>{ogrenci.ogrenciNo}</div>
                  </td>
                  <td>{ogrenci.ekuantGunler.length}</td>
                  <td>{ogrenci.toplamGun > 0 ? `${ogrenci.toplamGun} geldi` : "—"}</td>
                  <td>{statusLabel(ogrenci.status)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && kind === "ekuant" && (
        <EkuantSheet yil={yil} ay={ay} birimAdi={birimAdi} weeks={weeks} ogrenciler={ogrenciler} />
      )}
      {data && kind === "puantaj" && (
        <PuantajSheet
          yil={yil}
          ay={ay}
          birimAdi={birimAdi}
          gunlukSaat={gunlukSaat}
          daysInMonth={daysInMonth}
          ogrenciler={ogrenciler}
        />
      )}
    </Shell>
  );
}

export function EkuantSheet({
  yil,
  ay,
  birimAdi,
  weeks,
  ogrenciler
}: {
  yil: number;
  ay: number;
  birimAdi: string;
  weeks: (string | null)[][];
  ogrenciler: BirimRaporOgrenci[];
}) {
  const main = weeks.slice(0, 4);
  const extra = weeks.slice(4);
  const groups = extra.length === 0
    ? main.map((week, index) => ({ title: `${index + 1}. HAFTA`, weeks: [week] }))
    : [
        ...main.map((week, index) => ({ title: `${index + 1}. HAFTA`, weeks: [week] })),
        { title: "ARTIK GÜNLER", weeks: extra }
      ];

  return (
    <article className="report-sheet">
      <header className="report-head">
        <p>EK-6: İŞKUR Gençlik Programı Katılımcı Gün Çizelgesi</p>
        <h1>T.C. GAZİANTEP ÜNİVERSİTESİ</h1>
        <h2>{upper(birimAdi)} İŞKUR GENÇLİK KATILIMCI GÜN ÇİZELGESİ</h2>
        <p className="report-note">KATILIMCILAR İÇİN HAFTADA EN FAZLA 3 DEVAM GÜNÜ SEÇİNİZ</p>
        <p className="report-month">{upper(MONTHS[ay - 1])} ({yil})</p>
      </header>
      <table className="report-table">
        <thead>
          <tr>
            <th rowSpan={2}>SIRA</th>
            <th rowSpan={2}>TC</th>
            <th rowSpan={2}>AD SOYAD</th>
            {groups.map((group) => (
              <th key={group.title} colSpan={group.weeks.length * 7}>{group.title}</th>
            ))}
          </tr>
          <tr>
            {groups.flatMap((group, gi) =>
              group.weeks.flatMap((week, wi) =>
                WEEKDAYS.map((label, di) => (
                  <th key={`${gi}-${wi}-${di}`} className="day-head">
                    {label}
                    {week[di] ? <small>{Number(week[di]!.slice(8, 10))}</small> : null}
                  </th>
                ))
              )
            )}
          </tr>
        </thead>
        <tbody>
          {ogrenciler.map((row) => {
            const selected = new Set(row.ekuantGunler.map(ymd));
            return (
              <tr key={row.basvuruId}>
                <td>{row.siraNo}</td>
                <td>{row.tcKimlikNo || ""}</td>
                <td className="name">{upper(`${row.ad} ${row.soyad}`)}</td>
                {groups.flatMap((group, gi) =>
                  group.weeks.flatMap((week, wi) =>
                    week.map((date, di) => (
                      <td key={`${row.basvuruId}-${gi}-${wi}-${di}`} className={date && selected.has(date) ? "mark" : ""}>
                        {date && selected.has(date) ? "X" : ""}
                      </td>
                    ))
                  )
                )}
              </tr>
            );
          })}
          {ogrenciler.length === 0 && (
            <tr>
              <td colSpan={3 + weeks.length * 7}>Bu birime atanmış öğrenci yok.</td>
            </tr>
          )}
        </tbody>
      </table>
    </article>
  );
}

export function PuantajSheet({
  yil,
  ay,
  birimAdi,
  gunlukSaat,
  daysInMonth,
  ogrenciler
}: {
  yil: number;
  ay: number;
  birimAdi: string;
  gunlukSaat: number;
  daysInMonth: number;
  ogrenciler: BirimRaporOgrenci[];
}) {
  const days = Array.from({ length: daysInMonth }, (_, index) => index + 1);
  const saatLabel = formatSaat(gunlukSaat);

  return (
    <article className="report-sheet">
      <header className="report-head">
        <h1>T.C. GAZİANTEP ÜNİVERSİTESİ</h1>
        <h2>{upper(birimAdi)}</h2>
        <p>
          İŞKUR GENÇLİK PROGRAMI KAPSAMINDA ÇALIŞAN ÖĞRENCİLERİN {yil} YILI {upper(MONTHS[ay - 1])} AYI
          PUANTAJ CETVELİDİR
        </p>
      </header>
      <table className="report-table">
        <thead>
          <tr>
            <th rowSpan={2}>SIRA NO</th>
            <th rowSpan={2}>T.C. KİMLİK NUMARASI</th>
            <th rowSpan={2}>ADI</th>
            <th rowSpan={2}>SOYADI</th>
            <th rowSpan={2}>HALKBANK IBAN NO</th>
            <th colSpan={daysInMonth}>ÇALIŞTIĞI GÜNLER</th>
            <th rowSpan={2}>TOPLAM SAAT</th>
            <th rowSpan={2}>TOPLAM GÜN</th>
          </tr>
          <tr>
            {days.map((day) => (
              <th key={day} className="day-head">{day}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {ogrenciler.map((row) => {
            const geldi = new Set(row.geldiGunler.map((value) => Number(ymd(value).slice(8, 10))));
            return (
              <tr key={row.basvuruId}>
                <td>{row.siraNo}</td>
                <td>{row.tcKimlikNo || ""}</td>
                <td className="name">{upper(row.ad)}</td>
                <td className="name">{upper(row.soyad)}</td>
                <td className="iban">{row.iban || ""}</td>
                {days.map((day) => (
                  <td key={day} className={geldi.has(day) ? "mark" : ""}>
                    {geldi.has(day) ? saatLabel : ""}
                  </td>
                ))}
                <td>{row.toplamGun > 0 ? formatSaat(row.toplamSaat) : ""}</td>
                <td>{row.toplamGun > 0 ? row.toplamGun : ""}</td>
              </tr>
            );
          })}
          {ogrenciler.length === 0 && (
            <tr>
              <td colSpan={7 + daysInMonth}>Bu birime atanmış öğrenci yok.</td>
            </tr>
          )}
        </tbody>
      </table>
    </article>
  );
}
