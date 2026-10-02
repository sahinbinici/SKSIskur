import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { PageHeader } from "../components/PageHeader";
import { AdminProcessNav } from "../components/AdminProcessNav";
import { OgrenciKimlikMeta, Shell, formatDate } from "../components/ui";
import type { DagitimBirim, DagitimBirimi, DagitimSonuc } from "../types";

export function AdminDistribution() {
  const confirm = useConfirm();
  const [data, setData] = useState<DagitimSonuc | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [onlyFilled, setOnlyFilled] = useState(true);
  const [units, setUnits] = useState<DagitimBirimi[]>([]);
  const navigate = useNavigate();

  function load() {
    return Promise.all([api.dagitim(), api.dagitimBirimleri()]).then(([distribution, configuredUnits]) => {
      setData(distribution); setUnits(configuredUnits);
    });
  }

  async function moveStudent(basvuruId: number, birimKodu: string) {
    setBusy(true); setError(""); setMessage("");
    try {
      const result = await api.moveStudentUnit(basvuruId, birimKodu);
      setData(result);
      setMessage("Öğrencinin birim ataması manuel olarak güncellendi.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Öğrenci taşınamadı.");
    } finally { setBusy(false); }
  }

  useEffect(() => {
    load().catch((err) => setError(err instanceof ApiError ? err.message : "Dağıtım bilgisi alınamadı."));
  }, []);

  async function run(yeniden: boolean) {
    const confirmText = yeniden
      ? "Mevcut atamalar silinip kesin kayıttaki öğrenciler yeniden dağıtılacak. Devam edilsin mi?"
      : "Kesin kayıtlı ve henüz atanmamış öğrenciler birimlere dağıtılacak. Devam edilsin mi?";
    if (!await confirm({
      title: yeniden ? "Dağıtımı yeniden yap" : "Birim dağıtımını başlat",
      message: confirmText,
      confirmLabel: yeniden ? "Yeniden dağıt" : "Dağıt",
      variant: yeniden ? "danger" : "primary"
    })) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await api.dagit(yeniden);
      setData(result);
      setMessage(`${result.atanan} öğrenci dağıtıldı. Fakülte öncelikli: ${result.fakulteOncelikli}, rastgele: ${result.rastgele}.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Dağıtım yapılamadı.");
    } finally {
      setBusy(false);
    }
  }

  const birimler = useMemo(() => {
    const list = data?.birimler ?? [];
    return onlyFilled ? list.filter((b) => b.dolu > 0) : list;
  }, [data, onlyFilled]);

  return (
    <Shell home="/admin">
      <PageHeader
        title="Birim dağıtımı"
        description="Sözleşme imzalayan öğrenciler önce kendi fakülte/MYO birimine yerleştirilir; kontenjan dolarsa kalanlar diğer açık birimlere atanır."
      />
      <AdminProcessNav />
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      {data && !data.kesinListeOnaylandi && (
        <div className="alert alert-wait">
          Önce kesin kayıt ekranından İŞKUR nihai listesini yükleyin.
        </div>
      )}
      {data && data.kesinListeOnaylandi && !data.imzaBildirimiGonderildi && (
        <div className="alert alert-wait">
          Dağıtımdan önce nihai listedekilere sözleşme imza daveti gönderilmelidir.
        </div>
      )}
      {data?.imzaBildirimiGonderildi && (
        <div className="alert alert-wait">
          Dağıtıma yalnızca &quot;İmza geldi&quot; ile işaretlenen öğrenciler alınır.
        </div>
      )}
      <div className="grid-5" style={{ marginBottom: 18 }}>
        <Stat title="Kesin kayıt" value={data?.onaylanan} />
        <Stat title="Atanan" value={data?.atanan} />
        <Stat title="Fakülte öncelikli" value={data?.fakulteOncelikli} />
        <Stat title="Rastgele" value={data?.rastgele} />
        <Stat title="Atanamayan" value={data?.atanamayan} />
      </div>
      <div className="row" style={{ marginBottom: 16 }}>
        <button className="btn btn-gold" disabled={busy || !data?.kesinListeOnaylandi || !data?.imzaBildirimiGonderildi} onClick={() => run(false)}>Listeyi dağıt</button>
        <button className="btn btn-danger" disabled={busy || !data?.kesinListeOnaylandi || !data?.imzaBildirimiGonderildi} onClick={() => run(true)}>Yeniden dağıt</button>
        <label style={{ display: "flex", alignItems: "center", gap: 8, margin: 0 }}>
          <input type="checkbox" checked={onlyFilled} onChange={(e) => setOnlyFilled(e.target.checked)} style={{ width: "auto", margin: 0 }} />
          Yalnız dolu birimler
        </label>
      </div>

      <div className="card" style={{ overflow: "auto", marginBottom: 18 }}>
        <table>
          <thead>
            <tr>
              <th>Birim</th>
              <th>Dolu</th>
              <th>Kalan</th>
              <th>Doluluk</th>
            </tr>
          </thead>
          <tbody>
            {birimler.map((birim) => (
              <tr key={birim.kod}>
                <td>
                  <b>{birim.ad}</b>
                  <div style={{ color: "var(--muted)", fontSize: 12 }}>Kod {birim.kod}</div>
                </td>
                <td>{birim.dolu}</td>
                <td>{birim.kalan}</td>
                <td style={{ minWidth: 140 }}>
                  <div className="occ"><span style={{ width: `${Math.min(100, (birim.dolu / Math.max(1, birim.kontenjan)) * 100)}%` }} /></div>
                </td>
              </tr>
            ))}
            {birimler.length === 0 && (
              <tr><td colSpan={4} style={{ color: "var(--muted)" }}>Henüz atama yok. Onaylanan öğrenciler için “Listeyi dağıt” deyin.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {birimler.filter((b) => b.dolu > 0).map((birim) => (
        <UnitStudents key={birim.kod} birim={birim} units={units.filter((unit) => unit.dagitimaAcik && unit.kontenjan > 0)} busy={busy} onMove={moveStudent} onOpen={(id) => navigate(`/admin/basvuru/${id}`)} />
      ))}

      {(data?.atanamayanlar.length ?? 0) > 0 && (
        <section className="card" style={{ padding: 18 }}>
          <h3 className="section">Kontenjan dışı kalanlar</h3>
          {data?.atanamayanlar.map((row) => (
            <div key={row.basvuruId} className="doc-item" onClick={() => navigate(`/admin/basvuru/${row.basvuruId}`)} style={{ cursor: "pointer" }}>
              <div>
                <b>{row.adSoyad}</b>
                <OgrenciKimlikMeta ogrenciNo={row.ogrenciNo} tcKimlikNo={row.tcKimlikNo} />
                <div style={{ color: "var(--muted)", fontSize: 13 }}>{row.fakulte}</div>
              </div>
            </div>
          ))}
        </section>
      )}
      {data?.atamaTarihi && (
        <p style={{ color: "var(--muted)", fontSize: 13 }}>Son güncelleme: {formatDate(data.atamaTarihi)}</p>
      )}
    </Shell>
  );
}

function UnitStudents({ birim, units, busy, onMove, onOpen }: { birim: DagitimBirim; units: DagitimBirimi[]; busy: boolean; onMove: (id: number, unitCode: string) => void; onOpen: (id: number) => void }) {
  return (
    <section className="card" style={{ padding: 18, marginBottom: 14 }}>
      <h3 className="section">{birim.ad} <span style={{ color: "var(--muted)", fontSize: 14 }}>({birim.dolu}/{birim.kontenjan})</span></h3>
      <table>
        <thead>
          <tr>
            <th>Öğrenci</th>
            <th>Fakülte</th>
            <th>Atama</th>
            <th>Manuel taşı</th>
            <th><span className="sr-only">İncele</span></th>
          </tr>
        </thead>
        <tbody>
          {birim.ogrenciler.map((row) => (
            <tr key={row.basvuruId}>
              <td>
                <b>{row.adSoyad}</b>
                <OgrenciKimlikMeta ogrenciNo={row.ogrenciNo} tcKimlikNo={row.tcKimlikNo} />
              </td>
              <td>{row.fakulte || "—"}</td>
              <td>{row.atamaTuru === "FAKULTE" ? "Fakülte öncelikli" : row.atamaTuru === "MANUEL" ? "Manuel" : "Rastgele"}</td>
              <td><select className="unit-move-select" value={birim.kod} disabled={busy} onChange={(event) => { if (event.target.value !== birim.kod) void onMove(row.basvuruId, event.target.value); }}>
                <option value={birim.kod}>{birim.ad} (mevcut birim)</option>
                {units.filter((unit) => unit.kod !== birim.kod).map((unit) => <option key={unit.id} value={unit.kod}>{unit.ad} (kontenjan {unit.kontenjan})</option>)}
              </select></td>
              <td><button type="button" className="btn btn-secondary btn-compact" onClick={() => onOpen(row.basvuruId)}>İncele</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function Stat({ title, value }: { title: string; value?: number }) {
  return (
    <div className="card stat">
      <b>{value ?? "—"}</b>
      <span>{title}</span>
    </div>
  );
}
