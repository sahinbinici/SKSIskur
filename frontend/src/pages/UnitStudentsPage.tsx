import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../api";
import { useAuth } from "../auth";
import { EmptyState } from "../components/EmptyState";
import { UnitDashboardSummary } from "../components/UnitDashboardSummary";
import { Shell, formatDate } from "../components/ui";
import type { BirimAylikRapor, BirimDuyuruInbox, BirimOgrenci } from "../types";

export function UnitStudentsPage() {
  const { session } = useAuth();
  const [items, setItems] = useState<BirimOgrenci[]>([]);
  const [duyurular, setDuyurular] = useState<BirimDuyuruInbox[]>([]);
  const [report, setReport] = useState<BirimAylikRapor | null>(null);
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const now = new Date();

  useEffect(() => {
    Promise.all([
      api.birimOgrenciler(),
      api.birimDuyurular(),
      api.birimRapor(now.getFullYear(), now.getMonth() + 1).catch(() => null)
    ])
      .then(([nextItems, nextDuyurular, nextReport]) => {
        setItems(nextItems);
        setDuyurular(nextDuyurular);
        setReport(nextReport);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Liste alınamadı."));
  }, []);

  const unread = useMemo(() => duyurular.filter((item) => !item.okundu), [duyurular]);

  async function dismissDuyuru(id: number) {
    try {
      const updated = await api.markBirimDuyuruOkundu(id);
      setDuyurular((current) => current.map((item) => (item.id === id ? updated : item)));
    } catch {
      // ignore
    }
  }

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
        {session?.birimAdi || session?.displayName}. Öğrenci satırından EK-6 ve puantajı doldurup SKS’ye gönderin; onay sonrası Aylık rapor ekranından cetvel indirir veya yazdırırsınız (ödeme dosyası birimde yok).
      </p>
      {error && <div className="alert alert-error">{error}</div>}

      <UnitDashboardSummary
        items={items}
        unreadDuyuru={unread.length}
        report={report}
        birimAdi={session?.birimAdi || session?.displayName}
      />

      {unread.map((duyuru) => (
        <div className="alert alert-wait duyuru-banner" key={duyuru.id}>
          <div>
            <strong>{duyuru.baslik}</strong>
            <div style={{ marginTop: 6, whiteSpace: "pre-wrap" }}>{duyuru.mesaj}</div>
            <div style={{ color: "var(--muted)", fontSize: 12, marginTop: 8 }}>
              {formatDate(duyuru.gonderimTarihi)} · {duyuru.gonderenAdmin}
            </div>
          </div>
          <button type="button" className="btn btn-secondary btn-compact" onClick={() => void dismissDuyuru(duyuru.id)}>
            Okudum
          </button>
        </div>
      ))}

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
                  <b className={item.kalanIzinGunu === 0 ? "leave-balance exhausted" : "leave-balance"}>
                    {item.kalanIzinGunu === 0 ? "İzin hakkı bitti" : `${item.kalanIzinGunu} gün kaldı`}
                  </b>
                  <div style={{ color: "var(--muted)", fontSize: 12 }}>
                    {item.kullanilanIzinGunu}/{item.izinGunLimiti} gün kullanıldı
                    {item.kalanIzinGunu === 0 ? " · yeni izin girilemez" : ""}
                  </div>
                </td>
                <td>
                  <div>{item.eposta || "—"}</div>
                  <div style={{ color: "var(--muted)" }}>{item.gsm || ""}</div>
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={4}>
                  <EmptyState title="Öğrenci bulunamadı" description="Arama kriterlerinize uygun atanan öğrenci yok." />
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </Shell>
  );
}
