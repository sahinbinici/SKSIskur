import { useEffect, useRef } from "react";
import { api, ApiError, downloadAuthenticatedFile } from "../../api";
import { FilterChips } from "../../components/FilterChips";
import {
  ADMIN_BELGE_TIPLERI,
  BELGE_YUKLEME_FILTRE_LABEL,
  STATUS_LABEL,
  type ApplicationStatus,
  type BasvuruDonemi,
  type BelgeYuklemeFiltre,
  type DocumentType
} from "../../types";

type Props = {
  mode: "review" | "export";
  periods: BasvuruDonemi[];
  periodId?: number;
  setPeriodId: (id: number | undefined) => void;
  onlyMine: boolean;
  setOnlyMine: (value: boolean) => void;
  status: ApplicationStatus | "";
  setStatus: (value: ApplicationStatus | "") => void;
  query: string;
  setQuery: (value: string) => void;
  belgeTipi: DocumentType | "";
  setBelgeTipi: (value: DocumentType | "") => void;
  belgeYukleme: BelgeYuklemeFiltre;
  setBelgeYukleme: (value: BelgeYuklemeFiltre) => void;
  fakulte: string;
  setFakulte: (value: string) => void;
  fakulteler: string[];
  itemsCount: number;
  setError: (message: string) => void;
  load: (
    nextStatus?: ApplicationStatus | "",
    nextQuery?: string,
    nextPeriodId?: number,
    nextOnlyMine?: boolean,
    nextBelgeTipi?: DocumentType | "",
    nextBelgeYukleme?: BelgeYuklemeFiltre,
    nextFakulte?: string
  ) => Promise<void>;
};

export function AdminBasvuruToolbar(props: Props) {
  const {
    mode, periods, periodId, setPeriodId, onlyMine, setOnlyMine,
    status, setStatus, query, setQuery, belgeTipi, setBelgeTipi, belgeYukleme, setBelgeYukleme,
    fakulte, setFakulte, fakulteler, itemsCount, setError, load
  } = props;
  const debounceRef = useRef<number | undefined>(undefined);
  const skipQueryDebounce = useRef(true);

  const selectedPeriod = periods.find((period) => period.id === periodId);
  const selectedBelge = ADMIN_BELGE_TIPLERI.find((belge) => belge.type === belgeTipi);
  const belgeZipLabel = selectedBelge ? `${selectedBelge.title} indir (ZIP)` : "Belge ZIP indir";

  async function runLoad(
    nextStatus = status,
    nextQuery = query,
    nextPeriodId = periodId,
    nextOnlyMine = onlyMine,
    nextBelgeTipi = belgeTipi,
    nextBelgeYukleme = belgeYukleme,
    nextFakulte = fakulte
  ) {
    try {
      await load(nextStatus, nextQuery, nextPeriodId, nextOnlyMine, nextBelgeTipi, nextBelgeYukleme, nextFakulte);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Liste alınamadı.");
    }
  }

  useEffect(() => {
    if (skipQueryDebounce.current) {
      skipQueryDebounce.current = false;
      return;
    }
    window.clearTimeout(debounceRef.current);
    debounceRef.current = window.setTimeout(() => {
      void runLoad();
    }, 350);
    return () => window.clearTimeout(debounceRef.current);
  }, [query]);

  return (
    <>
      <div className="filter-panel no-print">
        <div className="filter-panel-section">
          <h4 className="filter-panel-title">Listeyi daralt</h4>
          <div className="filter-panel-row">
            <label className="filter-label">
              Dönem
              <select
                value={periodId ?? ""}
                onChange={(e) => {
                  const next = Number(e.target.value) || undefined;
                  setPeriodId(next);
                  void runLoad(status, query, next);
                }}
              >
                {periods.map((period) => (
                  <option key={period.id} value={period.id}>
                    {period.ad}{period.aktif ? " (açık)" : " (arşiv)"}
                  </option>
                ))}
              </select>
            </label>
            <label className="filter-label">
              Fakülte
              <select
                value={fakulte}
                onChange={(e) => {
                  const next = e.target.value;
                  setFakulte(next);
                  void runLoad(status, query, periodId, onlyMine, belgeTipi, belgeYukleme, next);
                }}
              >
                <option value="">Tümü</option>
                {fakulteler.map((name) => (
                  <option key={name} value={name}>{name}</option>
                ))}
              </select>
            </label>
            <label className="filter-label">
              Durum
              <select
                value={status}
                onChange={(e) => {
                  const value = e.target.value as ApplicationStatus | "";
                  setStatus(value);
                  void runLoad(value, query);
                }}
              >
                <option value="">Tümü</option>
                <option value="DRAFT">Taslak</option>
                <option value="SUBMITTED">İncelemede</option>
                <option value="RETURNED">İade edildi</option>
                <option value="APPROVED">Onaylandı</option>
                <option value="REJECTED">Reddedildi</option>
              </select>
            </label>
            <label className="filter-label filter-label-grow">
              Ara
              <input
                placeholder="Ad, soyad, T.C. veya öğrenci no"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") void runLoad();
                }}
              />
            </label>
            <label className="filter-label-check">
              <input
                type="checkbox"
                checked={onlyMine}
                onChange={(e) => {
                  const next = e.target.checked;
                  setOnlyMine(next);
                  void runLoad(status, query, periodId, next);
                }}
              />
              Bana atananlar
            </label>
            <button type="button" className="btn btn-primary" onClick={() => void runLoad()}>Ara</button>
          </div>
          <p className="filter-panel-note" style={{ marginTop: 8 }}>
            Arama ad, soyad, T.C. ve öğrenci numarasında çalışır. İnceleme sekmesi varsayılan olarak yalnızca incelemedeki kayıtları gösterir; tüm öğrenciler için Durum = Tümü seçin.
          </p>
        </div>

        {mode === "export" && (
          <div className="filter-panel-section">
            <h4 className="filter-panel-title">Belge filtresi (isteğe bağlı)</h4>
            <div className="filter-panel-row">
              <label className="filter-label">
                Belge türü
                <select
                  value={belgeTipi}
                  onChange={(e) => {
                    const value = e.target.value as DocumentType | "";
                    setBelgeTipi(value);
                    const nextYukleme = value ? belgeYukleme : "TUMU";
                    if (!value) setBelgeYukleme("TUMU");
                    void runLoad(status, query, periodId, onlyMine, value, nextYukleme);
                  }}
                >
                  <option value="">Tüm belgeler</option>
                  {ADMIN_BELGE_TIPLERI.map((belge) => (
                    <option key={belge.type} value={belge.type}>{belge.title}</option>
                  ))}
                </select>
              </label>
              <label className="filter-label">
                Yüklenme
                <select
                  value={belgeYukleme}
                  disabled={!belgeTipi}
                  onChange={(e) => {
                    const value = e.target.value as BelgeYuklemeFiltre;
                    setBelgeYukleme(value);
                    void runLoad(status, query, periodId, onlyMine, belgeTipi, value);
                  }}
                >
                  {(Object.keys(BELGE_YUKLEME_FILTRE_LABEL) as BelgeYuklemeFiltre[]).map((key) => (
                    <option key={key} value={key}>{BELGE_YUKLEME_FILTRE_LABEL[key]}</option>
                  ))}
                </select>
              </label>
            </div>
          </div>
        )}

        {mode === "export" && (
          <div className="filter-panel-section filter-panel-actions">
            <h4 className="filter-panel-title">İndirme</h4>
            <p className="filter-panel-note">Seçili filtreler Excel, ZIP ve yazdırma çıktısına uygulanır.</p>
            <div className="filter-panel-row">
              <button
                type="button"
                className="btn btn-gold"
                disabled={itemsCount === 0}
                onClick={() => downloadAuthenticatedFile(
                  api.adminBasvurularExcelUrl(status, query, periodId, onlyMine, belgeTipi, belgeYukleme, fakulte),
                  "basvurular.xlsx"
                ).catch((err) => setError(err instanceof ApiError ? err.message : "Excel indirilemedi."))}
              >
                Excel indir
              </button>
              {belgeTipi ? (
                <button
                  type="button"
                  className="btn btn-gold"
                  disabled={itemsCount === 0 || belgeYukleme === "YOK"}
                  onClick={() => downloadAuthenticatedFile(
                    api.adminBasvurularBelgeZipUrl(status, query, periodId, onlyMine, belgeTipi, belgeYukleme, fakulte),
                    api.adminBasvurularBelgeZipFilename(belgeTipi)
                  ).catch((err) => setError(err instanceof ApiError ? err.message : "Dosyalar indirilemedi."))}
                >
                  {belgeZipLabel}
                </button>
              ) : (
                <span className="filter-panel-note">Toplu belge ZIP için belge türü seçin.</span>
              )}
              <button type="button" className="btn btn-secondary" disabled={itemsCount === 0} onClick={() => window.print()}>
                Yazdır / PDF
              </button>
            </div>
          </div>
        )}
      </div>

      <FilterChips
        items={[
          selectedPeriod ? { label: selectedPeriod.ad, onClear: () => { setPeriodId(undefined); void runLoad(status, query, undefined); } } : { label: "" },
          fakulte ? { label: fakulte, onClear: () => { setFakulte(""); void runLoad(status, query, periodId, onlyMine, belgeTipi, belgeYukleme, ""); } } : { label: "" },
          status ? { label: STATUS_LABEL[status], onClear: () => { setStatus(""); void runLoad("", query); } } : { label: "" },
          onlyMine ? { label: "Bana atananlar", onClear: () => { setOnlyMine(false); void runLoad(status, query, periodId, false); } } : { label: "" },
          query.trim() ? { label: `Arama: ${query.trim()}`, onClear: () => { setQuery(""); void runLoad(status, ""); } } : { label: "" },
          selectedBelge ? { label: `Belge: ${selectedBelge.title}`, onClear: () => { setBelgeTipi(""); setBelgeYukleme("TUMU"); void runLoad(status, query, periodId, onlyMine, "", "TUMU"); } } : { label: "" },
          belgeTipi && belgeYukleme !== "TUMU" ? { label: BELGE_YUKLEME_FILTRE_LABEL[belgeYukleme], onClear: () => { setBelgeYukleme("TUMU"); void runLoad(status, query, periodId, onlyMine, belgeTipi, "TUMU"); } } : { label: "" }
        ]}
      />
    </>
  );
}
