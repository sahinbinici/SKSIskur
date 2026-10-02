import { Link } from "react-router-dom";
import { useMemo } from "react";
import { useAdminPeriodOps } from "../hooks/useAdminPeriodOps";

type PendingItem = {
  title: string;
  detail: string;
  count: number;
  link: string;
};

export function AdminPendingTasks() {
  const { period, ozet, kayit, dagitim, takip } = useAdminPeriodOps();

  const items = useMemo(() => {
    const pending: PendingItem[] = [];
    const review = ozet?.gonderildi ?? 0;
    if (review > 0) {
      pending.push({
        title: "İncelemede başvuru",
        detail: "Onay, red veya iade bekliyor",
        count: review,
        link: "/admin"
      });
    }
    if ((ozet?.onaylandi ?? 0) > 0 && !kayit?.kesinListeYuklendi) {
      pending.push({
        title: "İŞKUR nihai listesi",
        detail: "Onaylı listeyi indirin, dönen dosyayı yükleyin",
        count: ozet?.onaylandi ?? 0,
        link: "/admin/kayit"
      });
    }
    if (kayit?.kesinOnaylandi && !kayit.imzaBildirimiGonderildi) {
      pending.push({
        title: "Sözleşme daveti",
        detail: "Nihai listedekilere imza e-postası gönderilmeli",
        count: kayit.kesinListede ?? 0,
        link: "/admin/kayit"
      });
    }
    const imzaBekleyen = kayit?.imzaBildirimiGonderildi
      ? Math.max(0, (kayit.kesinListede ?? 0) - (dagitim?.onaylanan ?? 0))
      : 0;
    if (imzaBekleyen > 0) {
      pending.push({
        title: "Fiziksel imza",
        detail: "İmza geldi / pasife al işlemi bekliyor",
        count: imzaBekleyen,
        link: "/admin/kayit"
      });
    }
    const atanmamis = Math.max(0, (dagitim?.onaylanan ?? 0) - (dagitim?.atanan ?? 0));
    if (kayit?.imzaBildirimiGonderildi && atanmamis > 0) {
      pending.push({
        title: "Birim ataması",
        detail: "İmza gelen öğrenci birime atanmayı bekliyor",
        count: atanmamis,
        link: "/admin/dagitim"
      });
    }
    if ((dagitim?.atanamayan ?? 0) > 0) {
      pending.push({
        title: "Atanamayan öğrenci",
        detail: "Kontenjan yetersizliği",
        count: dagitim?.atanamayan ?? 0,
        link: "/admin/dagitim"
      });
    }
    const puantajOnay = takip?.ogrenciler.filter((row) => row.status === "SUBMITTED").length ?? 0;
    if (puantajOnay > 0) {
      pending.push({
        title: "Puantaj onayı",
        detail: "Birimin gönderdiği EK-6 / puantaj bekliyor",
        count: puantajOnay,
        link: "/admin/takip"
      });
    }
    return pending;
  }, [ozet, kayit, dagitim, takip]);

  const total = items.reduce((sum, item) => sum + item.count, 0);

  if (items.length === 0) {
    return (
      <section className="card dashboard-summary no-print">
        <div className="dashboard-summary-head">
          <div>
            <h4>Bekleyen işler</h4>
            <p>{period ? `${period.ad} · kritik iş yok` : "Aktif dönem yükleniyor…"}</p>
          </div>
          <span className="dashboard-badge dashboard-badge-ok">Güncel</span>
        </div>
      </section>
    );
  }

  return (
    <section className="card dashboard-summary no-print">
      <div className="dashboard-summary-head">
        <div>
          <h4>Bekleyen işler</h4>
          <p>{period ? period.ad : "Aktif dönem"} · {total} kayıt</p>
        </div>
        <span className="dashboard-badge">{items.length} iş kalemi</span>
      </div>
      <div className="dashboard-pending-grid">
        {items.map((item) => (
          <Link to={item.link} className="dashboard-pending-card" key={item.title}>
            <strong>{item.count}</strong>
            <span>{item.title}</span>
            <p>{item.detail}</p>
          </Link>
        ))}
      </div>
    </section>
  );
}
