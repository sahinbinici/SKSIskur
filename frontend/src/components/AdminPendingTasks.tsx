import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { api } from "../api";
import type { AdminOzet, BasvuruDonemi, DagitimSonuc, KayitListesi } from "../types";

type PendingItem = {
  title: string;
  detail: string;
  count: number;
  link: string;
};

export function AdminPendingTasks() {
  const [period, setPeriod] = useState<BasvuruDonemi>();
  const [ozet, setOzet] = useState<AdminOzet | null>(null);
  const [kayit, setKayit] = useState<KayitListesi | null>(null);
  const [dagitim, setDagitim] = useState<DagitimSonuc | null>(null);

  useEffect(() => {
    Promise.all([
      api.basvuruDonemleri(),
      api.kayitListesi().catch(() => null),
      api.dagitim().catch(() => null)
    ])
      .then(async ([periods, kayitData, dagitimData]) => {
        const active = periods.find((item) => item.aktif);
        setPeriod(active);
        setKayit(kayitData);
        setDagitim(dagitimData);
        if (active) {
          setOzet(await api.adminSummary(active.id));
        }
      })
      .catch(() => undefined);
  }, []);

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
    const kayitBekleyen = kayit?.kesinKarsilastirmaBekleyen ?? ozet?.kayitBekleyen ?? 0;
    if (kayitBekleyen > 0) {
      pending.push({
        title: "Kesin liste karşılaştırması",
        detail: "İŞKUR listesi ile eşleştirme bekliyor",
        count: kayitBekleyen,
        link: "/admin/kayit"
      });
    }
    if (kayit?.kesinOnaylandi && !kayit.imzaBildirimiGonderildi) {
      pending.push({
        title: "İmza bildirimi",
        detail: "Dağıtımdan önce gönderilmeli",
        count: kayit.kesinListede ?? 0,
        link: "/admin/kayit"
      });
    }
    const atanmamis = Math.max(0, (dagitim?.onaylanan ?? 0) - (dagitim?.atanan ?? 0));
    if (kayit?.imzaBildirimiGonderildi && atanmamis > 0) {
      pending.push({
        title: "Birim ataması",
        detail: "Kesin kayıtlı öğrenci birime atanmayı bekliyor",
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
    if ((kayit?.listedeBasvuruEslesmedi ?? 0) > 0) {
      pending.push({
        title: "Listede eşleşmeyen kayıt",
        detail: "İŞKUR listesinde olup başvuru bulunamadı",
        count: kayit?.listedeBasvuruEslesmedi ?? 0,
        link: "/admin/kayit"
      });
    }
    return pending;
  }, [ozet, kayit, dagitim]);

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
