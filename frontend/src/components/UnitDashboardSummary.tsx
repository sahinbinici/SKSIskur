import { Link } from "react-router-dom";
import type { BirimAylikRapor, BirimOgrenci } from "../types";

const MONTHS = [
  "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
  "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
];

type UnitDashboardSummaryProps = {
  items: BirimOgrenci[];
  unreadDuyuru: number;
  report: BirimAylikRapor | null;
  birimAdi?: string | null;
};

export function UnitDashboardSummary({ items, unreadDuyuru, report, birimAdi }: UnitDashboardSummaryProps) {
  const puantajGirilen = report?.ogrenciler.filter((ogrenci) => ogrenci.toplamGun > 0).length ?? 0;
  const eksikPuantaj = Math.max(0, items.length - puantajGirilen);
  const monthLabel = report ? `${MONTHS[report.ay - 1]} ${report.yil}` : "Bu ay";

  return (
    <section className="dashboard-summary card">
      <div className="dashboard-summary-head">
        <div>
          <h4>Birim özeti</h4>
          <p>{birimAdi || report?.birimAdi || "Atanan öğrenciler"} · {monthLabel}</p>
        </div>
        {unreadDuyuru > 0 && <span className="dashboard-badge">{unreadDuyuru} yeni duyuru</span>}
      </div>
      <div className="dashboard-pending-grid dashboard-pending-grid-4">
        <article className="dashboard-pending-card dashboard-stat-card">
          <strong>{items.length}</strong>
          <span>Aktif öğrenci</span>
        </article>
        <article className="dashboard-pending-card dashboard-stat-card">
          <strong>{unreadDuyuru}</strong>
          <span>Okunmamış duyuru</span>
        </article>
        <article className="dashboard-pending-card dashboard-stat-card">
          <strong>{puantajGirilen}/{items.length || "0"}</strong>
          <span>Puantaj girişi</span>
        </article>
        <Link to="/birim/rapor" className="dashboard-pending-card dashboard-stat-card dashboard-stat-link">
          <strong>
            {report?.yazdirilabilir
              ? "Hazır"
              : report?.gonderilebilir
                ? "Gönder"
                : report?.tamamlanmamisOgrenci ?? eksikPuantaj}
          </strong>
          <span>
            {report?.yazdirilabilir
              ? "Cetvel onaylı · İndir / yazdır →"
              : report?.gonderilebilir
                ? "Ayı SKS’ye gönder →"
                : report && report.onayBekleyenOgrenci > 0
                  ? "Onay bekliyor · Rapor →"
                  : "Eksik kayıt · Rapor →"}
          </span>
        </Link>
      </div>
    </section>
  );
}
