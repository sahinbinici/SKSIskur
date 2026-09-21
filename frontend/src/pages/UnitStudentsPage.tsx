import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../api";
import { useAuth } from "../auth";
import { Shell } from "../components/ui";
import type { BirimOgrenci } from "../types";

export function UnitStudentsPage() {
  const { session } = useAuth();
  const [items, setItems] = useState<BirimOgrenci[]>([]);
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  useEffect(() => {
    api.birimOgrenciler()
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLocaleLowerCase("tr-TR");
    if (!q) return items;
    return items.filter((item) =>
      [item.adSoyad, item.ogrenciNo, item.fakulte, item.bolum, item.program]
        .filter(Boolean)
        .some((value) => value!.toLocaleLowerCase("tr-TR").includes(q))
    );
  }, [items, query]);

  return (
    <Shell home="/birim">
      <h3 className="section">Birim öğrenci listesi</h3>
      <p style={{ color: "var(--muted)", marginTop: -8, marginBottom: 18 }}>
        {session?.birimAdi || session?.displayName}. Atanan öğrenciye tıklayarak EK-6 ve puantaj takvimlerini doldurun.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="toolbar">
        <input
          placeholder="Ad, soyad veya öğrenci no"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="btn btn-gold" onClick={() => navigate("/birim/rapor")}>Aylık rapor</button>
      </div>
      <div className="card" style={{ overflow: "auto" }}>
        <table>
          <thead>
            <tr>
              <th>Öğrenci</th>
              <th>Fakülte / program</th>
              <th>İzin bakiyesi</th>
              <th>İletişim</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item) => (
              <tr
                key={item.basvuruId}
                className="clickable"
                onClick={() => navigate(`/birim/ogrenci/${item.basvuruId}`)}
              >
                <td>
                  <b>{item.adSoyad}</b>
                  <div style={{ color: "var(--muted)" }}>{item.ogrenciNo}</div>
                </td>
                <td>{item.fakulte || item.program || item.bolum || "—"}</td>
                <td>
                  <b className={item.kalanIzinGunu === 0 ? "leave-balance exhausted" : "leave-balance"}>{item.kalanIzinGunu} gün kaldı</b>
                  <div style={{ color: "var(--muted)", fontSize: 12 }}>{item.kullanilanIzinGunu}/{item.izinGunLimiti} gün kullanıldı</div>
                </td>
                <td>
                  <div>{item.eposta || "—"}</div>
                  <div style={{ color: "var(--muted)" }}>{item.gsm || ""}</div>
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={4} style={{ color: "var(--muted)" }}>
                  Bu birime atanmış öğrenci bulunamadı.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </Shell>
  );
}
