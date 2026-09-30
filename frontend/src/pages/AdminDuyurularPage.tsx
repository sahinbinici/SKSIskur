import { FormEvent, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, ApiError } from "../api";
import { useConfirm } from "../components/ConfirmDialog";
import { PageHeader } from "../components/PageHeader";
import { Shell, formatDate } from "../components/ui";
import { AdminPanoDuyurulariPanel } from "./AdminPanoDuyurulariPanel";
import { AdminOgrenciDuyurulariPanel } from "./AdminOgrenciDuyurulariPanel";
import type { BirimDuyuru, DagitimBirimi } from "../types";

const emptyForm = {
  baslik: "",
  mesaj: "",
  tumBirimler: true
};

export function AdminDuyurularPage() {
  const confirm = useConfirm();
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get("sekme");
  const tab = tabParam === "pano" ? "pano" : tabParam === "ogrenci" ? "ogrenci" : "birim";
  const [units, setUnits] = useState<DagitimBirimi[]>([]);
  const [history, setHistory] = useState<BirimDuyuru[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [unitQuery, setUnitQuery] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  function loadBirim() {
    return Promise.all([api.dagitimBirimleri(), api.adminBirimDuyurulari()]).then(([nextUnits, nextHistory]) => {
      setUnits(nextUnits);
      setHistory(nextHistory);
    });
  }

  useEffect(() => {
    if (tab !== "birim") return;
    loadBirim().catch((err) => setError(err instanceof ApiError ? err.message : "Duyuru verileri alınamadı."));
  }, [tab]);

  const filteredUnits = useMemo(() => {
    const q = unitQuery.trim().toLocaleLowerCase("tr-TR");
    if (!q) return units;
    return units.filter((unit) =>
      [unit.kod, unit.ad].some((value) => value.toLocaleLowerCase("tr-TR").includes(q))
    );
  }, [units, unitQuery]);

  function toggleUnit(kod: string) {
    setSelected((current) => (current.includes(kod) ? current.filter((item) => item !== kod) : [...current, kod]));
  }

  function selectAllFiltered() {
    setSelected((current) => {
      const next = new Set(current);
      filteredUnits.forEach((unit) => next.add(unit.kod));
      return [...next];
    });
  }

  function clearSelection() {
    setSelected([]);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!form.baslik.trim() || !form.mesaj.trim()) {
      setError("Başlık ve duyuru metni zorunludur.");
      return;
    }
    if (!form.tumBirimler && selected.length === 0) {
      setError("Tüm birimler seçili değilse en az bir birim işaretleyin.");
      return;
    }
    const ok = await confirm({
      title: "Duyuruyu gönder",
      message: form.tumBirimler
        ? "Duyuru tüm birimlere iletilecek. Devam edilsin mi?"
        : `Duyuru ${selected.length} birime iletilecek. Devam edilsin mi?`,
      confirmLabel: "Gönder"
    });
    if (!ok) return;

    setBusy(true);
    setError("");
    try {
      const result = await api.sendBirimDuyurusu({
        baslik: form.baslik.trim(),
        mesaj: form.mesaj.trim(),
        tumBirimler: form.tumBirimler,
        birimKodlari: form.tumBirimler ? [] : selected
      });
      setMessage(`Duyuru ${result.hedefSayisi} birime gönderildi.`);
      setForm(emptyForm);
      setSelected([]);
      await loadBirim();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Duyuru gönderilemedi.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell home="/admin">
      <PageHeader
        title="Duyurular"
        description="Birimlere, yönetici ana sayfasına ve öğrenci giriş sayfasına duyuru gönderin."
      />

      <div className="page-tabs" style={{ marginBottom: 18 }}>
        <button
          type="button"
          className={tab === "birim" ? "active" : ""}
          onClick={() => setSearchParams({})}
        >
          Birim duyuruları
        </button>
        <button
          type="button"
          className={tab === "pano" ? "active" : ""}
          onClick={() => setSearchParams({ sekme: "pano" })}
        >
          Yönetici ana sayfa
        </button>
        <button
          type="button"
          className={tab === "ogrenci" ? "active" : ""}
          onClick={() => setSearchParams({ sekme: "ogrenci" })}
        >
          Öğrenci giriş sayfası
        </button>
      </div>

      {tab === "pano" ? (
        <AdminPanoDuyurulariPanel />
      ) : tab === "ogrenci" ? (
        <AdminOgrenciDuyurulariPanel />
      ) : (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          {message && <div className="alert alert-ok">{message}</div>}

          <form className="card" style={{ padding: 22, marginBottom: 18 }} onSubmit={(e) => void submit(e)}>
            <h3 className="section">Yeni birim duyurusu</h3>
            <label>Başlık</label>
            <input
              value={form.baslik}
              onChange={(e) => setForm({ ...form, baslik: e.target.value })}
              placeholder="Örn. Aylık puantaj son tarihi"
              maxLength={200}
            />
            <label>Duyuru metni</label>
            <textarea
              rows={5}
              value={form.mesaj}
              onChange={(e) => setForm({ ...form, mesaj: e.target.value })}
              placeholder="Birimlere iletilecek açıklama"
              maxLength={4000}
            />
            <label className="checkbox-row">
              <input
                type="checkbox"
                checked={form.tumBirimler}
                onChange={(e) => setForm({ ...form, tumBirimler: e.target.checked })}
              />
              Tüm birimlere gönder
            </label>

            {!form.tumBirimler && (
              <div className="duyuru-unit-picker">
                <div className="section-row">
                  <strong>Hedef birimler ({selected.length} seçili)</strong>
                  <div className="row">
                    <button type="button" className="btn btn-secondary btn-compact" onClick={selectAllFiltered}>
                      Listeyi seç
                    </button>
                    <button type="button" className="btn btn-secondary btn-compact" onClick={clearSelection}>
                      Seçimi temizle
                    </button>
                  </div>
                </div>
                <input
                  placeholder="Birim kodu veya adı ara"
                  value={unitQuery}
                  onChange={(e) => setUnitQuery(e.target.value)}
                />
                <div className="duyuru-unit-list">
                  {filteredUnits.map((unit) => (
                    <label key={unit.id} className="duyuru-unit-item">
                      <input
                        type="checkbox"
                        checked={selected.includes(unit.kod)}
                        onChange={() => toggleUnit(unit.kod)}
                      />
                      <span>
                        <b>{unit.ad}</b>
                        <span className="duyuru-unit-code">{unit.kod}</span>
                      </span>
                    </label>
                  ))}
                  {filteredUnits.length === 0 && <p style={{ color: "var(--muted)" }}>Birim bulunamadı.</p>}
                </div>
              </div>
            )}

            <div className="row" style={{ marginTop: 16 }}>
              <button className="btn btn-primary" disabled={busy}>
                {busy ? "Gönderiliyor…" : "Duyuruyu gönder"}
              </button>
            </div>
          </form>

          <section className="card" style={{ padding: 22 }}>
            <h3 className="section">Gönderim geçmişi</h3>
            <div style={{ overflow: "auto" }}>
              <table>
                <thead>
                  <tr>
                    <th>Tarih</th>
                    <th>Başlık</th>
                    <th>Hedef</th>
                    <th>Okunma</th>
                    <th>Gönderen</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map((item) => (
                    <tr key={item.id}>
                      <td>{formatDate(item.gonderimTarihi)}</td>
                      <td>
                        <b>{item.baslik}</b>
                        <div style={{ color: "var(--muted)", fontSize: 13, maxWidth: 420, whiteSpace: "pre-wrap" }}>
                          {item.mesaj}
                        </div>
                      </td>
                      <td>
                        {item.tumBirimler ? "Tüm birimler" : `${item.hedefSayisi} birim`}
                        {!item.tumBirimler && item.birimKodlari.length > 0 && (
                          <div style={{ color: "var(--muted)", fontSize: 12 }}>{item.birimKodlari.join(", ")}</div>
                        )}
                      </td>
                      <td>{item.okunanSayisi}/{item.hedefSayisi}</td>
                      <td>{item.gonderenAdmin}</td>
                    </tr>
                  ))}
                  {history.length === 0 && (
                    <tr>
                      <td colSpan={5} style={{ color: "var(--muted)" }}>Henüz duyuru gönderilmedi.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </Shell>
  );
}
