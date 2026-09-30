import { useEffect, useRef, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { useAuth } from "../auth";
import { isSuperAdmin, STATUS_LABEL, type ApplicationStatus } from "../types";
import type { ReactNode } from "react";

type NavMenuId = "operasyon" | "yonetim" | "diger";

function NavDropdown({
  id,
  label,
  openMenu,
  setOpenMenu,
  className,
  children
}: {
  id: NavMenuId;
  label: string;
  openMenu: NavMenuId | null;
  setOpenMenu: (value: NavMenuId | null) => void;
  className?: string;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const open = openMenu === id;

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpenMenu(null);
      }
    }
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, [open, setOpenMenu]);

  return (
    <div className={`nav-menu${open ? " is-open" : ""}${className ? ` ${className}` : ""}`} ref={ref}>
      <button
        type="button"
        className="nav-menu-trigger"
        aria-expanded={open}
        onClick={() => setOpenMenu(open ? null : id)}
      >
        {label}
      </button>
      <div className="nav-menu-links" onClick={() => setOpenMenu(null)}>
        {children}
      </div>
    </div>
  );
}

function AdminBasvuruNavLink() {
  const location = useLocation();
  const isExport = new URLSearchParams(location.search).get("mod") === "indir";
  const active = location.pathname === "/admin" && !isExport;
  return <NavLink to="/admin" className={`nav-link${active ? " active" : ""}`}>Başvurular</NavLink>;
}

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return <span className={`badge ${status}`}>{STATUS_LABEL[status]}</span>;
}

export function Shell({ children, home, wide, full }: { children: ReactNode; home: string; wide?: boolean; full?: boolean }) {
  const { session, logout } = useAuth();
  const superAdmin = isSuperAdmin(session);
  const [openMenu, setOpenMenu] = useState<NavMenuId | null>(null);

  const operasyonLinks = (
    <>
      <NavLink to="/admin/kayit">Kesin kayıt</NavLink>
      <NavLink to="/admin/dagitim">Birim dağıtımı</NavLink>
      <NavLink to="/admin/duyurular">Duyurular</NavLink>
    </>
  );

  const yonetimLinks = (
    <>
      <NavLink to="/admin/donemler">Dönemler</NavLink>
      <NavLink to="/admin/birimler">Birimler ve kontenjanlar</NavLink>
      <NavLink to="/admin/sozlesmeler">Sözleşmeler</NavLink>
      {superAdmin && <NavLink to="/admin/yoneticiler">Yöneticiler</NavLink>}
      {superAdmin && <NavLink to="/admin/kullanicilar">Birim hesapları</NavLink>}
      <NavLink to="/admin/islem-loglari">İşlem günlüğü</NavLink>
    </>
  );

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
              <AdminBasvuruNavLink />
              <NavLink to="/admin/takip" className="nav-link nav-link-wide">Aylık İŞKUR</NavLink>
              <NavDropdown id="operasyon" label="Operasyon" openMenu={openMenu} setOpenMenu={setOpenMenu} className="nav-desktop-only">
                {operasyonLinks}
              </NavDropdown>
              <NavDropdown id="yonetim" label="Yönetim" openMenu={openMenu} setOpenMenu={setOpenMenu} className="nav-desktop-only">
                {yonetimLinks}
              </NavDropdown>
              <NavDropdown id="diger" label="Menü" openMenu={openMenu} setOpenMenu={setOpenMenu} className="nav-compact-only">
                {operasyonLinks}
                {yonetimLinks}
              </NavDropdown>
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
