import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/EmptyState";
import { StatusBadge, formatDate } from "../../components/ui";
import { ADMIN_BELGE_TIPLERI, kesinListeDurumLabel, type ApplicationStatus, type Basvuru, type DocumentType } from "../../types";

const PAGE_SIZE = 50;

export function belgeCellLabel(item: Basvuru, belgeTipi: DocumentType | "") {
  if (!belgeTipi) {
    return `${item.belgeler.length}/5`;
  }
  const docs = item.belgeler.filter((belge) => belge.belgeTipi === belgeTipi);
  if (docs.length === 0) {
    return "Eksik";
  }
  return docs.map((belge) => belge.orijinalAd).join(", ");
}

type StatFilter = ApplicationStatus | "ASSIGNED" | "ALL";

export function BasvuruStat({
  title,
  value,
  active,
  listCount,
  onClick
}: {
  title: string;
  value?: number;
  active?: boolean;
  listCount?: number;
  onClick?: () => void;
}) {
  const content = (
    <>
      <b>{value ?? "—"}</b>
      <span>{title}</span>
      {listCount != null && <small className="stat-list-count">Listede {listCount}</small>}
    </>
  );
  if (!onClick) {
    return <div className="card stat">{content}</div>;
  }
  return (
    <button type="button" className={`card stat stat-button${active ? " active" : ""}`} onClick={onClick}>
      {content}
    </button>
  );
}

export function statFilterForStatus(status: ApplicationStatus | "", onlyMine: boolean): StatFilter {
  if (onlyMine && !status) return "ASSIGNED";
  if (!status) return "ALL";
  return status;
}

export function BasvuruTable({
  items,
  belgeTipi = "",
  reviewMode = false,
  emptyHint
}: {
  items: Basvuru[];
  belgeTipi?: DocumentType | "";
  reviewMode?: boolean;
  emptyHint?: string;
}) {
  const [page, setPage] = useState(1);
  const [sortKey, setSortKey] = useState<"name" | "date">("date");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const belgeColumnTitle = ADMIN_BELGE_TIPLERI.find((belge) => belge.type === belgeTipi)?.title ?? "Belgeler";

  const sorted = useMemo(() => {
    const next = [...items];
    next.sort((a, b) => {
      const left = sortKey === "name"
        ? a.student.adSoyad.localeCompare(b.student.adSoyad, "tr-TR")
        : (a.gonderimTarihi ?? "").localeCompare(b.gonderimTarihi ?? "");
      return sortDir === "asc" ? left : -left;
    });
    return next;
  }, [items, sortDir, sortKey]);

  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const pageItems = sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  function toggleSort(key: "name" | "date") {
    if (sortKey === key) {
      setSortDir((value) => (value === "asc" ? "desc" : "asc"));
      return;
    }
    setSortKey(key);
    setSortDir(key === "name" ? "asc" : "desc");
    setPage(1);
  }

  if (items.length === 0) {
    return (
      <div className="card">
        <EmptyState
          title="Kayıt bulunamadı"
          description={emptyHint ?? "Filtreleri değiştirin veya farklı bir dönem seçin."}
        />
      </div>
    );
  }

  return (
    <>
      <div className="card" style={{ overflow: "auto" }}>
        <table>
          <thead>
            <tr>
              <th>
                <button type="button" className="table-sort" onClick={() => toggleSort("name")}>
                  Öğrenci {sortKey === "name" ? (sortDir === "asc" ? "↑" : "↓") : ""}
                </button>
              </th>
              <th>Fakülte</th>
              <th>Atanan birim</th>
              <th>İnceleme sorumlusu</th>
              <th>Durum</th>
              <th>Kesin liste</th>
              <th>
                <button type="button" className="table-sort" onClick={() => toggleSort("date")}>
                  Gönderim {sortKey === "date" ? (sortDir === "asc" ? "↑" : "↓") : ""}
                </button>
              </th>
              <th>{belgeColumnTitle}</th>
              <th><span className="sr-only">İşlem</span></th>
            </tr>
          </thead>
          <tbody>
            {pageItems.map((item) => (
              <tr key={item.id} className="table-row-link">
                <td>
                  <Link to={`/admin/basvuru/${item.id}`} className="table-row-anchor">
                    <b>{item.student.adSoyad}</b>
                    <div style={{ color: "var(--muted)", fontSize: 13 }}>
                      <div>{item.student.ogrenciNo}</div>
                      <div>T.C. {item.student.tcKimlikNo || "—"}</div>
                    </div>
                  </Link>
                </td>
                <td>{item.student.fakulte || item.student.program || item.student.bolum}</td>
                <td>{item.atananBirimAdi || "—"}</td>
                <td>{item.atananAdmin || "Atama bekliyor"}</td>
                <td><StatusBadge status={item.status} /></td>
                <td>{kesinListeDurumLabel(item.kesinListede, item.status)}</td>
                <td>{formatDate(item.gonderimTarihi)}</td>
                <td style={{ maxWidth: 220, wordBreak: "break-word" }}>{belgeCellLabel(item, belgeTipi)}</td>
                <td>
                  <Link to={`/admin/basvuru/${item.id}`} className="btn btn-secondary btn-compact">
                    {reviewMode ? "İncele" : "Aç"}
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {sorted.length > PAGE_SIZE && (
        <div className="table-pagination no-print">
          <span>{sorted.length} kayıt · Sayfa {page}/{totalPages}</span>
          <div className="row">
            <button type="button" className="btn btn-secondary btn-compact" disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>
              Önceki
            </button>
            <button type="button" className="btn btn-secondary btn-compact" disabled={page >= totalPages} onClick={() => setPage((value) => value + 1)}>
              Sonraki
            </button>
          </div>
        </div>
      )}
    </>
  );
}
