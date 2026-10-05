import { useEffect, useState } from "react";
import { api, ApiError } from "../../api";
import type { AdminOzet, ApplicationStatus, Basvuru, BasvuruDonemi, BelgeYuklemeFiltre, DocumentType } from "../../types";

export function useAdminBasvuruList(initialStatus: ApplicationStatus | "" = "SUBMITTED") {
  const [ozet, setOzet] = useState<AdminOzet | null>(null);
  const [items, setItems] = useState<Basvuru[]>([]);
  const [status, setStatus] = useState<ApplicationStatus | "">(initialStatus);
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [periods, setPeriods] = useState<BasvuruDonemi[]>([]);
  const [periodId, setPeriodId] = useState<number | undefined>();
  const [onlyMine, setOnlyMine] = useState(false);
  const [belgeTipi, setBelgeTipi] = useState<DocumentType | "">("");
  const [belgeYukleme, setBelgeYukleme] = useState<BelgeYuklemeFiltre>("TUMU");
  const [fakulte, setFakulte] = useState("");
  const [fakulteler, setFakulteler] = useState<string[]>([]);

  async function load(
    nextStatus = status,
    nextQuery = query,
    nextPeriodId = periodId,
    nextOnlyMine = onlyMine,
    nextBelgeTipi = belgeTipi,
    nextBelgeYukleme = belgeYukleme,
    nextFakulte = fakulte
  ) {
    setLoading(true);
    try {
      const [summary, list] = await Promise.all([
        api.adminSummary(nextPeriodId),
        api.adminList(nextStatus, nextQuery, nextPeriodId, nextOnlyMine, nextBelgeTipi, nextBelgeYukleme, nextFakulte)
      ]);
      setOzet(summary);
      setItems(list);
      setError("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Liste alınamadı.");
      throw err;
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load().catch(() => undefined);
  }, []);

  useEffect(() => {
    api.basvuruDonemleri()
      .then((data) => {
        setPeriods(data);
        setPeriodId(data.find((period) => period.aktif)?.id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Dönemler yüklenemedi."));
  }, []);

  useEffect(() => {
    api.adminFakulteler(periodId)
      .then(setFakulteler)
      .catch(() => setFakulteler([]));
  }, [periodId]);

  return {
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
    fakulte,
    setFakulte,
    fakulteler,
    load
  };
}
