import { Link, useSearchParams } from "react-router-dom";
import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "../api";
import { AdminProcessNav } from "../components/AdminProcessNav";
import { PageHeader } from "../components/PageHeader";
import { LoadingBlock } from "../components/LoadingBlock";
import { Shell, formatDate } from "../components/ui";
import { AdminWorkflowGuide } from "../components/AdminWorkflowGuide";
import { AdminPendingTasks } from "../components/AdminPendingTasks";
import { ApplicationListSheet } from "./AdminListReports";
import { AdminBasvuruToolbar } from "./admin/AdminBasvuruToolbar";
import { BasvuruStat, BasvuruTable, statFilterForStatus } from "./admin/BasvuruTable";
import { useAdminBasvuruList } from "./admin/useAdminBasvuruList";
import {
  ADMIN_BELGE_TIPLERI,
  BELGE_YUKLEME_FILTRE_LABEL,
  STATUS_LABEL,
  type ApplicationStatus,
  type YoneticiPanosuDuyuru
} from "../types";

export function AdminDashboard() {
  const [searchParams] = useSearchParams();
  const mode = searchParams.get("mod") === "indir" ? "export" : "review";
  const {
    ozet,
    items,
    status,
    setStatus,
    query,
    setQuery,
    error,
    setError,
    loading,
    periods,
    periodId,
    setPeriodId,
    onlyMine,
    setOnlyMine,
    belgeTipi,
    setBelgeTipi,
    belgeYukleme,
    setBelgeYukleme,
    load
  } = useAdminBasvuruList(mode === "export" ? "" : "SUBMITTED");
  const prevMode = useRef(mode);
  const [panoDuyurulari, setPanoDuyurulari] = useState<YoneticiPanosuDuyuru[]>([]);

  useEffect(() => {
    api.adminPanoDuyurulari(true)
      .then(setPanoDuyurulari)
      .catch(() => setPanoDuyurulari([]));
  }, []);

  useEffect(() => {
    if (prevMode.current === mode) return;
    prevMode.current = mode;
    if (mode === "export") {
      setStatus("");
      setOnlyMine(false);
      void load("", query, periodId, false, belgeTipi, belgeYukleme).catch((err) =>
        setError(err instanceof ApiError ? err.message : "Liste alınamadı.")
      );
      return;
    }
    setStatus("SUBMITTED");
    setOnlyMine(false);
    void load("SUBMITTED", query, periodId, false, belgeTipi, belgeYukleme).catch((err) =>
      setError(err instanceof ApiError ? err.message : "Liste alınamadı.")
    );
  }, [mode]);

  const selectedBelge = ADMIN_BELGE_TIPLERI.find((belge) => belge.type === belgeTipi);
  const activeStat = statFilterForStatus(status, onlyMine);
  const filterLabel = [
    status ? STATUS_LABEL[status] : "Tümü",
    query.trim() ? `Arama: ${query.trim()}` : "",
    selectedBelge && belgeYukleme !== "TUMU"
      ? `${selectedBelge.title}: ${BELGE_YUKLEME_FILTRE_LABEL[belgeYukleme]}`
      : selectedBelge
        ? `Belge: ${selectedBelge.title}`
        : ""
  ]
    .filter(Boolean)
    .join(" · ");

  function applyStatFilter(next: ApplicationStatus | "ASSIGNED" | "ALL") {
    if (next === "ASSIGNED") {
      setOnlyMine(true);
      setStatus("");
      void load("", query, periodId, true, belgeTipi, belgeYukleme).catch((err) =>
        setError(err instanceof ApiError ? err.message : "Liste alınamadı.")
      );
      return;
    }
    setOnlyMine(false);
    const nextStatus = next === "ALL" ? "" : next;
    setStatus(nextStatus);
    void load(nextStatus, query, periodId, false, belgeTipi, belgeYukleme).catch((err) =>
      setError(err instanceof ApiError ? err.message : "Liste alınamadı.")
    );
  }

  return (
    <Shell home="/admin">
      <div className="no-print">
        <PageHeader
          title="Başvurular"
          description="İncelemede olanları onaylayın. Süreçteki diğer işler üst menüde ve aşağıdaki bekleyen işlerde."
        />
        <AdminProcessNav />

        {panoDuyurulari.map((duyuru) => (
          <div className="alert alert-wait admin-pano-duyuru" key={duyuru.id}>
            <div>
              <strong>{duyuru.baslik}</strong>
              <div style={{ marginTop: 6, whiteSpace: "pre-wrap" }}>{duyuru.mesaj}</div>
              <div style={{ color: "var(--muted)", fontSize: 12, marginTop: 8 }}>
                {formatDate(duyuru.guncellemeTarihi)} · {duyuru.gonderenAdmin}
              </div>
            </div>
            <Link to="/admin/duyurular?sekme=pano" className="btn btn-secondary btn-compact">
              Duyuruları yönet
            </Link>
          </div>
        ))}

        <AdminPendingTasks />

        <AdminWorkflowGuide />

        <div className="page-tabs">
          <Link to="/admin" className={mode === "review" ? "active" : ""}>İnceleme</Link>
          <Link to="/admin?mod=indir" className={mode === "export" ? "active" : ""}>Liste indir</Link>
        </div>

        {error && <div className="alert alert-error">{error}</div>}

        {mode === "review" && (
          <div className="grid-5" style={{ marginBottom: 18 }}>
            <BasvuruStat title="Toplam" value={ozet?.toplam} listCount={activeStat === "ALL" && !onlyMine ? items.length : undefined} active={activeStat === "ALL" && !onlyMine} onClick={() => applyStatFilter("ALL")} />
            <BasvuruStat title="Taslak" value={ozet?.taslak} listCount={activeStat === "DRAFT" ? items.length : undefined} active={activeStat === "DRAFT"} onClick={() => applyStatFilter("DRAFT")} />
            <BasvuruStat title="İncelemede" value={ozet?.gonderildi} listCount={activeStat === "SUBMITTED" ? items.length : undefined} active={activeStat === "SUBMITTED"} onClick={() => applyStatFilter("SUBMITTED")} />
            <BasvuruStat title="Onaylandı" value={ozet?.onaylandi} listCount={activeStat === "APPROVED" ? items.length : undefined} active={activeStat === "APPROVED"} onClick={() => applyStatFilter("APPROVED")} />
            <BasvuruStat title="Bana atanan" value={ozet?.atanan} listCount={onlyMine ? items.length : undefined} active={onlyMine} onClick={() => applyStatFilter("ASSIGNED")} />
          </div>
        )}

        <AdminBasvuruToolbar
          mode={mode}
          periods={periods}
          periodId={periodId}
          setPeriodId={setPeriodId}
          onlyMine={onlyMine}
          setOnlyMine={setOnlyMine}
          status={status}
          setStatus={setStatus}
          query={query}
          setQuery={setQuery}
          belgeTipi={belgeTipi}
          setBelgeTipi={setBelgeTipi}
          belgeYukleme={belgeYukleme}
          setBelgeYukleme={setBelgeYukleme}
          itemsCount={items.length}
          setError={setError}
          load={load}
        />

        {loading ? (
          <LoadingBlock label="Başvuru listesi yükleniyor..." />
        ) : (
          <BasvuruTable
            items={items}
            belgeTipi={mode === "export" ? belgeTipi : undefined}
            reviewMode={mode === "review"}
            emptyHint={mode === "review" ? "İncelemede başvuru yok. Özet kartlarından farklı bir durum seçin." : undefined}
          />
        )}
      </div>
      {mode === "export" && <ApplicationListSheet items={items} filterLabel={filterLabel} />}
    </Shell>
  );
}
