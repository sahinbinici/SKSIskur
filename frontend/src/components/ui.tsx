import { NavLink } from "react-router-dom";
import { useAuth } from "../auth";
import { STATUS_LABEL, type ApplicationStatus } from "../types";
import type { ReactNode } from "react";

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return <span className={`badge ${status}`}>{STATUS_LABEL[status]}</span>;
}

export function Shell({ children, home, wide, full }: { children: ReactNode; home: string; wide?: boolean; full?: boolean }) {
  const { session, logout } = useAuth();
  return (
    <div className="page-shell">
      <header className="topbar">
        <NavLink to={home} className="brand">
          <div className="crest">GÜ</div>
          <div>
            <h1>İŞKUR Gençlik Programı</h1>
            <p>Gaziantep Üniversitesi SKS Daire Başkanlığı</p>
          </div>
        </NavLink>
        <div className="topbar-actions">
          {session?.role === "ADMIN" && (
            <>
              <NavLink to="/admin" className="nav-link">Başvurular</NavLink>
              <details className="nav-menu">
                <summary>Operasyon</summary>
                <div className="nav-menu-links">
                  <NavLink to="/admin/kayit">Kesin / yedek</NavLink>
                  <NavLink to="/admin/dagitim">Birim dağıtımı</NavLink>
                  <NavLink to="/admin/takip">Devam takibi</NavLink>
                </div>
              </details>
              <details className="nav-menu">
                <summary>Yönetim</summary>
                <div className="nav-menu-links">
                  <NavLink to="/admin/donemler">Dönemler</NavLink>
                  <NavLink to="/admin/birimler">Birimler ve kontenjanlar</NavLink>
                  <NavLink to="/admin/sozlesmeler">Sözleşmeler</NavLink>
                  <NavLink to="/admin/yoneticiler">Yöneticiler</NavLink>
                  <NavLink to="/admin/kullanicilar">Birim hesapları</NavLink>
                </div>
              </details>
            </>
          )}
          {session?.role === "BIRIM" && (
            <>
              <NavLink to="/birim" className="nav-link">Öğrenciler</NavLink>
              <NavLink to="/birim/rapor" className="nav-link">Aylık rapor</NavLink>
            </>
          )}
          <span className="user-chip">{session?.displayName}</span>
          <button className="btn btn-ghost" onClick={logout}>Çıkış</button>
        </div>
      </header>
      <main className={full ? "wrap wrap-full" : wide ? "wrap wrap-wide" : "wrap"}>{children}</main>
    </div>
  );
}

export function Field({ label, value }: { label: string; value?: string | null }) {
  return (
    <>
      <span>{label}</span>
      <b>{value || "—"}</b>
    </>
  );
}

export function formatDate(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("tr-TR");
}

export function maskTc(value?: string | null) {
  if (!value || value.length < 5) return value || "—";
  return `${value.slice(0, 3)}*****${value.slice(-3)}`;
}
