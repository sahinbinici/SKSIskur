import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth";
import { LoginPage } from "./pages/LoginPage";
import { StudentHome } from "./pages/StudentHome";
import { ApplicationPage } from "./pages/ApplicationPage";
import { AdminDashboard } from "./pages/AdminDashboard";
import { AdminDetail } from "./pages/AdminDetail";
import { AdminDistribution } from "./pages/AdminDistribution";
import { AdminKayitPage } from "./pages/AdminKayitPage";
import { AdminTakipPage } from "./pages/AdminTakipPage";
import { AdminTakipDetail } from "./pages/AdminTakipDetail";
import { AdminBirimKullanicilarPage } from "./pages/AdminBirimKullanicilarPage";
import { AdminPeriodsPage } from "./pages/AdminPeriodsPage";
import { AdminUsersPage } from "./pages/AdminUsersPage";
import { AdminAgreementsPage } from "./pages/AdminAgreementsPage";
import { AdminUnitsPage } from "./pages/AdminUnitsPage";
import { UnitStudentsPage } from "./pages/UnitStudentsPage";
import { UnitTrackingPage } from "./pages/UnitTrackingPage";
import { UnitReportPage } from "./pages/UnitReportPage";
import type { Role } from "./types";
import type { ReactNode } from "react";

function homeFor(role: Role) {
  if (role === "ADMIN") return "/admin";
  if (role === "BIRIM") return "/birim";
  return "/panel";
}

function Guard({ role, children }: { role: Role; children: ReactNode }) {
  const { session } = useAuth();
  if (!session) return <Navigate to="/" replace />;
  if (session.role !== role) {
    return <Navigate to={homeFor(session.role)} replace />;
  }
  return children;
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route path="/panel" element={<Guard role="STUDENT"><StudentHome /></Guard>} />
      <Route path="/basvuru" element={<Guard role="STUDENT"><ApplicationPage /></Guard>} />
      <Route path="/admin" element={<Guard role="ADMIN"><AdminDashboard /></Guard>} />
      <Route path="/admin/donemler" element={<Guard role="ADMIN"><AdminPeriodsPage /></Guard>} />
      <Route path="/admin/sozlesmeler" element={<Guard role="ADMIN"><AdminAgreementsPage /></Guard>} />
      <Route path="/admin/birimler" element={<Guard role="ADMIN"><AdminUnitsPage /></Guard>} />
      <Route path="/admin/basvuru/:id" element={<Guard role="ADMIN"><AdminDetail /></Guard>} />
      <Route path="/admin/kayit" element={<Guard role="ADMIN"><AdminKayitPage /></Guard>} />
      <Route path="/admin/dagitim" element={<Guard role="ADMIN"><AdminDistribution /></Guard>} />
      <Route path="/admin/kullanicilar" element={<Guard role="ADMIN"><AdminBirimKullanicilarPage /></Guard>} />
      <Route path="/admin/yoneticiler" element={<Guard role="ADMIN"><AdminUsersPage /></Guard>} />
      <Route path="/admin/takip" element={<Guard role="ADMIN"><AdminTakipPage /></Guard>} />
      <Route path="/admin/takip/:basvuruId" element={<Guard role="ADMIN"><AdminTakipDetail /></Guard>} />
      <Route path="/birim" element={<Guard role="BIRIM"><UnitStudentsPage /></Guard>} />
      <Route path="/birim/ogrenci/:basvuruId" element={<Guard role="BIRIM"><UnitTrackingPage /></Guard>} />
      <Route path="/birim/rapor" element={<Guard role="BIRIM"><UnitReportPage /></Guard>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
