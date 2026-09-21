import { FormEvent, useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { api, ApiError } from "../api";
import { useAuth } from "../auth";
import type { DemoInfo } from "../types";

type Mode = "student" | "admin" | "unit";

export function LoginPage() {
  const { session, loginStudent, loginAdmin, loginUnit } = useAuth();
  const [mode, setMode] = useState<Mode>("student");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [demo, setDemo] = useState<DemoInfo | null>(null);

  useEffect(() => {
    api.demoInfo().then(setDemo).catch(() => setDemo(null));
  }, []);

  if (session?.role === "STUDENT") return <Navigate to="/panel" replace />;
  if (session?.role === "ADMIN") return <Navigate to="/admin" replace />;
  if (session?.role === "BIRIM") return <Navigate to="/birim" replace />;

  function fill(nextMode: Mode, nextUser: string, nextPass: string) {
    setMode(nextMode);
    setUsername(nextUser);
    setPassword(nextPass);
    setError("");
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      if (mode === "student") {
        await loginStudent(username.trim(), password);
      } else if (mode === "admin") {
        await loginAdmin(username.trim(), password);
      } else {
        await loginUnit(username.trim(), password);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Giriş yapılamadı.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-layout">
      <section className="login-hero">
        <p>Sağlık Kültür ve Spor Daire Başkanlığı</p>
        <h2>İŞKUR Gençlik Programı Başvuru Portalı</h2>
        <p>
          Öğrenciler OBS hesabıyla giriş yapar, evraklarını yükler ve başvurularını gönderir.
          SKS yöneticileri başvuruları inceler ve birimlere dağıtır. Birim kullanıcıları
          atanan öğrencilerin EK-6 ve puantaj takibini yapar.
        </p>
      </section>
      <section className="login-panel">
        <form className="card login-card" onSubmit={onSubmit}>
          <div className="tabs tabs-3">
            <button type="button" className={mode === "student" ? "active" : ""} onClick={() => setMode("student")}>
              Öğrenci
            </button>
            <button type="button" className={mode === "admin" ? "active" : ""} onClick={() => setMode("admin")}>
              Yönetici
            </button>
            <button type="button" className={mode === "unit" ? "active" : ""} onClick={() => setMode("unit")}>
              Birim
            </button>
          </div>
          {error && <div className="alert alert-error">{error}</div>}
          <label>
            {mode === "student" ? "Öğrenci numarası" : "Kullanıcı adı"}
          </label>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
          <label>Şifre</label>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
          <button className="btn btn-primary btn-wide" disabled={loading}>
            {loading ? "Kontrol ediliyor..." : "Giriş yap"}
          </button>
          {mode === "student" && (
            <p style={{ color: "var(--muted)", fontSize: 13, marginTop: 14 }}>
              Giriş, üniversite öğrenci bilgi sistemi (Proliz) üzerinden doğrulanır. Şifre bu uygulamada saklanmaz.
            </p>
          )}
          {mode === "unit" && (
            <p style={{ color: "var(--muted)", fontSize: 13, marginTop: 14 }}>
              SKS yöneticisinin tanımladığı birim hesabıyla giriş yapılır.
            </p>
          )}
        </form>
        {demo?.enabled && (
          <aside className="card demo-accounts">
            <p className="demo-accounts-title">Test hesapları</p>
            <p className="demo-accounts-hint">Satıra tıklayınca bilgiler forma dolar. Canlı ortamda kapatılır.</p>
            <button
              type="button"
              className="demo-row"
              onClick={() => fill("admin", demo.adminUsername ?? "admin", demo.adminPassword ?? "")}
            >
              <span>Yönetici</span>
              <span>{demo.adminUsername} / {demo.adminPassword}</span>
            </button>
            <p className="demo-accounts-label">
              Öğrenciler ({demo.ogrenciSayisi} adet, 99010001–99010100) · şifre {demo.ogrenciSifre}
            </p>
            {demo.ogrenciler.map((ogrenci) => (
              <button
                key={ogrenci.ogrenciNo}
                type="button"
                className="demo-row"
                onClick={() => fill("student", ogrenci.ogrenciNo, demo.ogrenciSifre ?? "")}
              >
                <span>{ogrenci.ogrenciNo} · {ogrenci.adSoyad}</span>
                <span>{ogrenci.durum}</span>
              </button>
            ))}
            {demo.ogrenciSayisi > demo.ogrenciler.length && (
              <p className="demo-accounts-hint" style={{ marginBottom: 0 }}>
                Diğer demo öğrenciler: 99010007 – 9901{String(demo.ogrenciSayisi).padStart(4, "0")} (aynı şifre).
              </p>
            )}
            <p className="demo-accounts-label">Birimler · şifre {demo.birimSifre}</p>
            {demo.birimler.map((birim) => (
              <button
                key={birim.kod}
                type="button"
                className="demo-row"
                onClick={() => fill("unit", birim.kod, demo.birimSifre ?? "")}
              >
                <span>Kod {birim.kod}</span>
                <span>{birim.ad}</span>
              </button>
            ))}
          </aside>
        )}
      </section>
    </div>
  );
}
