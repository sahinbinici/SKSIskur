import { KAYIT_LABEL, STATUS_LABEL, type Basvuru, type KayitListesi, type KayitListesiSatir } from "../types";

function upper(value?: string | null) {
  return (value || "").toLocaleUpperCase("tr-TR");
}

function todayLabel() {
  return new Date().toLocaleDateString("tr-TR");
}

function dateLabel(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString("tr-TR");
}

function ListSignature({ onaylayan }: { onaylayan?: string | null }) {
  return (
    <div className="report-sign">
      <div>
        <b>HAZIRLAYAN</b>
        <span>Ad Soyad: ____________________________</span>
        <span>Ünvan: SKS Daire Başkanlığı</span>
        <span>Tarih: {todayLabel()}</span>
        <span>İmza</span>
      </div>
      <div>
        <b>ONAYLAYAN</b>
        <span>Ad Soyad: {onaylayan ? upper(onaylayan) : "____________________________"}</span>
        <span>Ünvan: ____________________________</span>
        <span>Tarih: ____________________________</span>
        <span>İmza</span>
      </div>
    </div>
  );
}

export function ApplicationListSheet({
  items,
  filterLabel
}: {
  items: Basvuru[];
  filterLabel: string;
}) {
  return (
    <article className="report-sheet print-only">
      <header className="report-head">
        <h1>T.C. GAZİANTEP ÜNİVERSİTESİ</h1>
        <h2>SAĞLIK KÜLTÜR VE SPOR DAİRE BAŞKANLIĞI</h2>
        <p>İŞKUR GENÇLİK PROGRAMI BAŞVURU LİSTESİ</p>
        <p className="report-note">
          Filtre: {upper(filterLabel)} · Düzenleme tarihi: {todayLabel()} · Toplam {items.length} öğrenci
        </p>
      </header>
      <table className="report-table report-table-list">
        <thead>
          <tr>
            <th>SIRA</th>
            <th>ÖĞRENCİ NO</th>
            <th>T.C. KİMLİK</th>
            <th>ADI</th>
            <th>SOYADI</th>
            <th>FAKÜLTE / BÖLÜM</th>
            <th>DURUM</th>
            <th>KAYIT</th>
            <th>BELGE</th>
            <th>GÖNDERİM</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item, index) => (
            <tr key={item.id}>
              <td>{index + 1}</td>
              <td>{item.student.ogrenciNo}</td>
              <td>{item.student.tcKimlikNo || ""}</td>
              <td className="name">{upper(item.student.ad)}</td>
              <td className="name">{upper(item.student.soyad)}</td>
              <td className="name">{upper(item.student.fakulte || item.student.program || item.student.bolum)}</td>
              <td>{STATUS_LABEL[item.status]}</td>
              <td>{item.kayitTuru ? KAYIT_LABEL[item.kayitTuru] : item.status === "APPROVED" ? "Bekliyor" : "—"}</td>
              <td>{item.belgeler.length}/5</td>
              <td>{dateLabel(item.gonderimTarihi) || "—"}</td>
            </tr>
          ))}
          {items.length === 0 && (
            <tr>
              <td colSpan={10}>Bu listede öğrenci yok.</td>
            </tr>
          )}
        </tbody>
      </table>
      <ListSignature />
    </article>
  );
}

function KayitTable({ rows, emptyText }: { rows: KayitListesiSatir[]; emptyText: string }) {
  return (
    <table className="report-table report-table-list">
      <thead>
        <tr>
          <th>SIRA</th>
          <th>ÖĞRENCİ NO</th>
          <th>T.C. KİMLİK</th>
          <th>ADI</th>
          <th>SOYADI</th>
          <th>FAKÜLTE / PROGRAM</th>
          <th>ATANAN BİRİM</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr key={row.basvuruId}>
            <td>{index + 1}</td>
            <td>{row.ogrenciNo}</td>
            <td>{row.tcKimlikNo || ""}</td>
            <td className="name">{upper(row.ad || row.adSoyad)}</td>
            <td className="name">{upper(row.soyad)}</td>
            <td className="name">{upper(row.fakulte || row.program || row.bolum)}</td>
            <td className="name">{upper(row.atananBirimAdi) || "—"}</td>
          </tr>
        ))}
        {rows.length === 0 && (
          <tr>
            <td colSpan={7}>{emptyText}</td>
          </tr>
        )}
      </tbody>
    </table>
  );
}

export function KayitListSheets({ data }: { data: KayitListesi }) {
  const kesin = data.ogrenciler.filter((row) => row.kayitTuru === "KESIN");
  const yedek = data.ogrenciler.filter((row) => row.kayitTuru === "YEDEK");
  const durum = data.kesinOnaylandi
    ? `Onaylandı${data.onaylayanAdmin ? ` (${data.onaylayanAdmin})` : ""}${data.onayTarihi ? ` · ${dateLabel(data.onayTarihi)}` : ""}`
    : "TASLAK — kesin liste henüz onaylanmadı";

  return (
    <>
      <article className="report-sheet print-only">
        <header className="report-head">
          <h1>T.C. GAZİANTEP ÜNİVERSİTESİ</h1>
          <h2>SAĞLIK KÜLTÜR VE SPOR DAİRE BAŞKANLIĞI</h2>
          <p>İŞKUR GENÇLİK PROGRAMI KESİN KAYIT LİSTESİ</p>
          <p className="report-note">{durum} · {kesin.length} öğrenci · {todayLabel()}</p>
        </header>
        <KayitTable rows={kesin} emptyText="Kesin kayda alınan öğrenci yok." />
        <ListSignature onaylayan={data.onaylayanAdmin} />
      </article>
      <article className="report-sheet print-only report-break">
        <header className="report-head">
          <h1>T.C. GAZİANTEP ÜNİVERSİTESİ</h1>
          <h2>SAĞLIK KÜLTÜR VE SPOR DAİRE BAŞKANLIĞI</h2>
          <p>İŞKUR GENÇLİK PROGRAMI YEDEK LİSTE</p>
          <p className="report-note">{durum} · {yedek.length} öğrenci · {todayLabel()}</p>
        </header>
        <KayitTable rows={yedek} emptyText="Yedek listede öğrenci yok." />
        <ListSignature onaylayan={data.onaylayanAdmin} />
      </article>
    </>
  );
}
