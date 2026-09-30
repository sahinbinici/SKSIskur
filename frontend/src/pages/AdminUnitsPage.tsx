import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, ApiError } from "../api";
import { PageHeader } from "../components/PageHeader";
import { Shell } from "../components/ui";
import type { DagitimBirimi } from "../types";

const emptyForm = { kod: "", ad: "", kontenjan: 0, dagitimaAcik: true };

export function AdminUnitsPage() {
  const [units, setUnits] = useState<DagitimBirimi[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  function load() {
    return api.dagitimBirimleri().then(setUnits);
  }
  useEffect(() => { load().catch((err) => setError(err instanceof ApiError ? err.message : "Birimler alınamadı.")); }, []);

  const filtered = useMemo(() => {
    const text = query.trim().toLocaleLowerCase("tr-TR");
    return text ? units.filter((unit) => `${unit.kod} ${unit.ad}`.toLocaleLowerCase("tr-TR").includes(text)) : units;
  }, [units, query]);

  async function saveUnit(unit: DagitimBirimi, patch: Partial<Pick<DagitimBirimi, "kontenjan" | "dagitimaAcik">>) {
    setBusy(true); setError(""); setMessage("");
    try {
      const updated = await api.updateDagitimBirimi(unit.id, {
        kontenjan: patch.kontenjan ?? unit.kontenjan,
        dagitimaAcik: patch.dagitimaAcik ?? unit.dagitimaAcik
      });
      setUnits((items) => items.map((item) => item.id === updated.id ? updated : item));
    } catch (err) { setError(err instanceof ApiError ? err.message : "Birim güncellenemedi."); }
    finally { setBusy(false); }
  }

  async function create(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(""); setMessage("");
    try {
      await api.createOzelDagitimBirimi({ ...form, kod: form.kod.trim(), ad: form.ad.trim(), kontenjan: Number(form.kontenjan) });
      setForm(emptyForm); await load(); setMessage("Özel birim eklendi.");
    } catch (err) { setError(err instanceof ApiError ? err.message : "Özel birim eklenemedi."); }
    finally { setBusy(false); }
  }

  return <Shell home="/admin" wide>
    <PageHeader title="Birimler ve kontenjanlar" description="Dağıtım birimlerini tanımlayın ve kontenjanları yönetin." />
    <p className="muted">Sicilden gelen birimler ilk açılışta buraya eklenir. Dağıtıma kapalı birimler öğrenci atamasına hiç dahil edilmez. Her birimin kontenjanı ayrı uygulanır.</p>
    {error && <div className="alert alert-error">{error}</div>}{message && <div className="alert alert-ok">{message}</div>}
    <form className="card" style={{ padding: 18, marginBottom: 18 }} onSubmit={create}>
      <h4 style={{ margin: "0 0 12px" }}>Sicilde olmayan özel birim</h4>
      <div className="grid-4">
        <div><label>Birim kodu</label><input value={form.kod} onChange={(e) => setForm({ ...form, kod: e.target.value })} placeholder="Örn. OZEL-01" required /></div>
        <div><label>Birim adı</label><input value={form.ad} onChange={(e) => setForm({ ...form, ad: e.target.value })} required /></div>
        <div><label>Kontenjan</label><input type="number" min="0" value={form.kontenjan} onChange={(e) => setForm({ ...form, kontenjan: Number(e.target.value) })} required /></div>
        <div><label style={{ display: "flex", gap: 8, alignItems: "center", marginTop: 30 }}><input type="checkbox" checked={form.dagitimaAcik} onChange={(e) => setForm({ ...form, dagitimaAcik: e.target.checked })} style={{ width: "auto", margin: 0 }} />Dağıtıma açık</label></div>
      </div>
      <button className="btn btn-gold" disabled={busy}>Özel birim ekle</button>
    </form>
    <div className="toolbar"><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Birim adı veya kod ara" /></div>
    <div className="card" style={{ overflow: "auto" }}><table><thead><tr><th>Birim</th><th>Kaynak</th><th>Kontenjan</th><th>Dağıtım durumu</th></tr></thead><tbody>
      {filtered.map((unit) => <tr key={unit.id}><td><b>{unit.ad}</b><div className="muted">Kod {unit.kod}</div></td><td>{unit.ozel ? "Özel" : "Sicil"}</td><td><input className="unit-quota-input" type="number" min="0" defaultValue={unit.kontenjan} disabled={busy} onBlur={(e) => { const value = Number(e.target.value); if (Number.isFinite(value) && value !== unit.kontenjan) void saveUnit(unit, { kontenjan: value }); }} /></td><td><button type="button" className={unit.dagitimaAcik ? "btn btn-ok btn-compact" : "btn btn-secondary btn-compact"} disabled={busy} onClick={() => void saveUnit(unit, { dagitimaAcik: !unit.dagitimaAcik })}>{unit.dagitimaAcik ? "Açık" : "Kapalı"}</button></td></tr>)}
      {filtered.length === 0 && <tr><td colSpan={4} className="muted">Birim bulunamadı.</td></tr>}
    </tbody></table></div>
  </Shell>;
}
