import { Link } from "react-router-dom";
import { useMemo, useState } from "react";
import { useAdminPeriodOps } from "../hooks/useAdminPeriodOps";

type StepState = "done" | "current" | "pending";

const STEPS = [
  { n: 1, title: "Dönem aç & İŞKUR listesi", link: "/admin/donemler", detail: "Öğrenci girişi ve başvuru kapısı" },
  { n: 2, title: "Başvuruları incele", link: "/admin", detail: "Onay, red, iade" },
  { n: 3, title: "Nihai liste & sözleşme", link: "/admin/kayit", detail: "İŞKUR’a gönder, dönen liste, imza" },
  { n: 4, title: "Birim dağıtımı", link: "/admin/dagitim", detail: "İmza gelenleri kontenjana yerleştir" },
  { n: 5, title: "Aylık devam ve ödeme", link: "/admin/takip", detail: "Puantaj onayı ve resmi Excel" }
] as const;

export function AdminWorkflowGuide() {
  const [open, setOpen] = useState(false);
  const { period, ozet, kayit, dagitim } = useAdminPeriodOps();

  const states = useMemo((): StepState[] => {
    const step1 = Boolean(period?.aktif && period.iskurListeYuklendi);
    const step2 = step1 && (ozet?.gonderildi ?? 0) === 0 && (ozet?.onaylandi ?? 0) > 0;
    const step3 = Boolean(kayit?.imzaBildirimiGonderildi);
    const step4 = (dagitim?.atanan ?? 0) > 0;
    const done = [step1, step2, step3, step4, false];
    const firstPending = done.findIndex((value) => !value);
    return done.map((value, index) => {
      if (value) return "done";
      if (firstPending === index) return "current";
      return "pending";
    });
  }, [period, ozet, kayit, dagitim]);

  const nextStep = STEPS[states.findIndex((state) => state === "current")] ?? null;
  const completed = states.filter((state) => state === "done").length;

  return (
    <section className="card workflow-guide no-print">
      <button type="button" className="workflow-guide-toggle" onClick={() => setOpen((value) => !value)}>
        <span>İş akışı · {completed}/{STEPS.length} adım tamam</span>
        <span className="workflow-guide-hint">{open ? "Gizle" : "Göster"}</span>
      </button>
      {nextStep && (
        <div className="workflow-next">
          <span>Sıradaki iş</span>
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
