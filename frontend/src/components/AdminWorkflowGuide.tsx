import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { api, ApiError } from "../api";
import type { AdminOzet, BasvuruDonemi, DagitimSonuc, KayitListesi } from "../types";

type StepState = "done" | "current" | "pending";

const STEPS = [
  { n: 1, title: "Dönem aç & İŞKUR listesi yükle", link: "/admin/donemler", detail: "Öğrenci girişi ve başvuru kapısı" },
  { n: 2, title: "Başvuruları incele", link: "/admin", detail: "Onay, red, iade" },
  { n: 3, title: "Kesin liste & karşılaştırma", link: "/admin/kayit", detail: "İŞKUR'dan dönen liste" },
  { n: 4, title: "İmza bildirimi gönder", link: "/admin/kayit", detail: "Dağıtımdan önce zorunlu" },
  { n: 5, title: "Birim dağıtımı", link: "/admin/dagitim", detail: "Kontenjana göre atama" },
  { n: 6, title: "Aylık devam & İŞKUR paketi", link: "/admin/takip", detail: "EK-6, puantaj, resmi Excel" }
] as const;

function stepStates(
  period: BasvuruDonemi | undefined,
  ozet: AdminOzet | null,
  kayit: KayitListesi | null,
  dagitim: DagitimSonuc | null
): StepState[] {
  const step1 = Boolean(period?.aktif && period.iskurListeYuklendi);
  const step2Done = step1 && (ozet?.gonderildi ?? 0) === 0 && (ozet?.onaylandi ?? 0) > 0;
  const step3Done = Boolean(kayit?.kesinOnaylandi);
  const step4Done = Boolean(kayit?.imzaBildirimiGonderildi);
  const step5Done = (dagitim?.atanan ?? 0) > 0;

  const done = [step1, step2Done, step3Done, step4Done, step5Done, false];
  const firstPending = done.findIndex((value) => !value);
  return done.map((value, index) => {
    if (value) return "done";
    if (firstPending === index) return "current";
    return "pending";
  });
}

export function AdminWorkflowGuide() {
  const [open, setOpen] = useState(true);
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

  const states = useMemo(() => stepStates(period, ozet, kayit, dagitim), [period, ozet, kayit, dagitim]);
  const nextStep = STEPS[states.findIndex((state) => state === "current")] ?? null;
  const completed = states.filter((state) => state === "done").length;

  return (
    <section className="card workflow-guide no-print">
      <button type="button" className="workflow-guide-toggle" onClick={() => setOpen((value) => !value)}>
        <span>İŞKUR iş akışı · {completed}/{STEPS.length} tamamlandı</span>
        <span className="workflow-guide-hint">{open ? "Gizle" : "Göster"}</span>
      </button>
      {nextStep && (
        <div className="workflow-next">
          <span>Sıradaki iş:</span>
          <Link to={nextStep.link} className="btn btn-primary btn-compact">{nextStep.title}</Link>
        </div>
      )}
      {open && (
        <ol className="workflow-steps">
          {STEPS.map((step, index) => (
            <li key={step.n} className={`workflow-step-${states[index]}`}>
              <span className="workflow-step-no">{states[index] === "done" ? "✓" : step.n}</span>
              <div>
                <Link to={step.link}>{step.title}</Link>
                <p>{step.detail}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
