import { NavLink } from "react-router-dom";
import { useAdminPeriodOps } from "../hooks/useAdminPeriodOps";

const STEPS = [
  { to: "/admin/donemler", label: "1. Dönem" },
  { to: "/admin", label: "2. Başvuru" },
  { to: "/admin/kayit", label: "3. Kesin kayıt" },
  { to: "/admin/dagitim", label: "4. Dağıtım" },
  { to: "/admin/takip", label: "5. Devam / ödeme" }
] as const;

export function AdminProcessNav() {
  const { period, ozet, kayit, dagitim } = useAdminPeriodOps();
  const current = (() => {
    if (!period?.aktif || !period.iskurListeYuklendi) return "/admin/donemler";
    if ((ozet?.gonderildi ?? 0) > 0) return "/admin";
    if (!kayit?.kesinListeYuklendi || !kayit.imzaBildirimiGonderildi) return "/admin/kayit";
    if ((dagitim?.atanan ?? 0) === 0) return "/admin/dagitim";
    return "/admin/takip";
  })();

  return (
    <nav className="process-nav no-print" aria-label="Süreç adımları">
      {STEPS.map((step) => (
        <NavLink
          key={step.to}
          to={step.to}
          className={({ isActive }) =>
            `process-nav-link${isActive ? " is-active" : ""}${current === step.to ? " is-next" : ""}`
          }
          end={step.to === "/admin"}
        >
          {step.label}
        </NavLink>
      ))}
    </nav>
  );
}
