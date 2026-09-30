import type { OgrenciCalismaOzet } from "../types";

const MONTHS = [
  "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
  "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
];

const TAKIP_LABEL: Record<string, string> = {
  DRAFT: "Hazırlanıyor",
  SUBMITTED: "Birime gönderildi"
};

type StudentWorkPanelProps = {
  ozet: OgrenciCalismaOzet;
};

export function StudentWorkPanel({ ozet }: StudentWorkPanelProps) {
  const monthLabel = `${MONTHS[ozet.ay - 1]} ${ozet.yil}`;
  const takipLabel = ozet.takipDurumu ? TAKIP_LABEL[ozet.takipDurumu] ?? ozet.takipDurumu : "Henüz başlamadı";

  return (
    <section className="card student-work-panel">
      <div className="dashboard-summary-head">
        <div>
          <h3 className="section">Çalışma paneli</h3>
          <p style={{ color: "var(--muted)", margin: 0 }}>
            {ozet.donemAdi || "Aktif dönem"} · {ozet.birimAdi}
          </p>
        </div>
        <span className="dashboard-badge dashboard-badge-ok">Atandınız</span>
      </div>
      <div className="dashboard-pending-grid dashboard-pending-grid-4">
        <article className="dashboard-pending-card dashboard-stat-card">
          <strong>{ozet.birimAdi}</strong>
          <span>Atandığınız birim</span>
        </article>
        <article className="dashboard-pending-card dashboard-stat-card">
          <strong>{ozet.kalanIzinGunu} gün</strong>
          <span>İzin bakiyesi ({ozet.kullanilanIzinGunu}/{ozet.izinGunLimiti})</span>
        </article>
        <article className="dashboard-pending-card dashboard-stat-card">
          <strong>{ozet.buAyTamGun}</strong>
          <span>{monthLabel} tam gün</span>
        </article>
        <article className="dashboard-pending-card dashboard-stat-card">
          <strong>{ozet.buAyEkuantGun}</strong>
          <span>EK-6 günü · {takipLabel}{ozet.takipKilitli ? " · kilitli" : ""}</span>
        </article>
      </div>
      <p className="student-work-hint">
        Puantaj ve EK-6 takviminizi birim sorumlunuz girer. Sorularınız için SKS Daire Başkanlığı ile iletişime geçin.
      </p>
    </section>
  );
}
