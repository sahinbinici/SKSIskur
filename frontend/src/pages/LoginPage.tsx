import { Navigate } from "react-router-dom";
import { LoginForm } from "../components/LoginForm";
import { LoginPortalHero } from "../components/LoginPortalHero";
import { useAuth } from "../auth";

export function LoginPage() {
  const { session, loginStudent, loginAdmin, loginUnit } = useAuth();

  if (session?.role === "STUDENT") return <Navigate to="/panel" replace />;
  if (session?.role === "ADMIN") return <Navigate to="/admin" replace />;
  if (session?.role === "BIRIM") return <Navigate to="/birim" replace />;

  return (
    <div className="login-layout">
      <section className="login-hero login-hero-scroll">
        <LoginPortalHero />
      </section>
      <section className="login-panel">
        <div className="login-panel-brand">
          <div className="crest">GÜ</div>
          <div>
            <strong>İŞKUR Gençlik Programı</strong>
            <span>Gaziantep Üniversitesi SKS Daire Başkanlığı</span>
          </div>
        </div>
        <LoginForm
          onStudentLogin={loginStudent}
          onAdminLogin={loginAdmin}
          onUnitLogin={loginUnit}
        />
      </section>
    </div>
  );
}
