import { Navigate } from "react-router-dom";

/** Eski /admin/raporlar adresi birleşik başvuru sayfasına yönlendirilir. */
export function AdminBasvuruReports() {
  return <Navigate to="/admin?mod=indir" replace />;
}
