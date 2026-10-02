import { useEffect, useState } from "react";
import { api } from "../api";
import type { AdminOzet, AdminTakipOzet, BasvuruDonemi, DagitimSonuc, KayitListesi } from "../types";

export function useAdminPeriodOps() {
  const [period, setPeriod] = useState<BasvuruDonemi>();
  const [ozet, setOzet] = useState<AdminOzet | null>(null);
  const [kayit, setKayit] = useState<KayitListesi | null>(null);
  const [dagitim, setDagitim] = useState<DagitimSonuc | null>(null);
  const [takip, setTakip] = useState<AdminTakipOzet | null>(null);

  useEffect(() => {
    const now = new Date();
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
          const [summary, monthTakip] = await Promise.all([
            api.adminSummary(active.id),
            api.adminTakip(now.getFullYear(), now.getMonth() + 1, undefined, active.id).catch(() => null)
          ]);
          setOzet(summary);
          setTakip(monthTakip);
        }
      })
      .catch(() => undefined);
  }, []);

  return { period, ozet, kayit, dagitim, takip };
}
