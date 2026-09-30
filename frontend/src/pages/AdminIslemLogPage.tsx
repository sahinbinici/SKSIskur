import { FormEvent, useEffect, useState } from "react";
import { api, ApiError } from "../api";
import { PageHeader } from "../components/PageHeader";
import { Shell, formatDate } from "../components/ui";
import type { IslemLog, IslemLogPage, IslemTuru, Role } from "../types";

const TUR_LABELS: Record<IslemTuru, string> = {
  GIRIS: "Giriş",
  BASVURU_TASLAK: "Başvuru taslağı",
  BASVURU_GONDERIM: "Başvuru gönderimi",
  BASVURU_ONAY: "Başvuru onayı",
  BASVURU_RED: "Başvuru reddi",
  BASVURU_IADE: "Başvuru iadesi",
  YONETICI_OLUSTUR: "Yönetici oluşturma",
  YONETICI_GUNCELLE: "Yönetici güncelleme",
  DONEM_AC: "Dönem açma",
  DONEM_KAPAT: "Dönem kapatma",
  ISKUR_LISTE_YUKLE: "İŞKUR listesi yükleme",
  KESIN_LISTE_YUKLE: "Kesin liste yükleme",
  KESIN_LISTE_ONAY: "Kesin liste onayı",
  KESIN_LISTE_GERI_AL: "Kesin liste geri alma",
  IMZA_BILDIRIMI: "İmza bildirimi",
  BIRIM_DUYURU_GONDER: "Birim duyurusu",
  YONETICI_PANO_DUYURU: "Ana sayfa duyurusu",
  OGRENCI_PANO_DUYURU: "Öğrenci portal duyurusu",
  BIRIM_DAGITIM: "Birim dağıtımı",
  BIRIM_ATAMA_DEGISTIR: "Manuel birim ataması",
  BASVURU_YONETICI_DAGITIM: "Yöneticiye dağıtım",
  KAPALI_GUN_KAYDET: "Kapalı gün kaydı",
  EKUANT_KAYDET: "Ekuant kaydı",
  PUANTAJ_KAYDET: "Puantaj kaydı",
  TAKIP_GONDER: "Takip gönderimi",
  ISKUR_PAKET_INDIR: "İŞKUR paketi indirme"
};

const ROL_LABELS: Record<Role, string> = {
  STUDENT: "Öğrenci",
  ADMIN: "Yönetici",
  BIRIM: "Birim"
};

const TUR_OPTIONS = Object.entries(TUR_LABELS) as [IslemTuru, string][];

type Filters = {
  tur: IslemTuru | "";
  rol: Role | "";
  kullanici: string;
  from: string;
  to: string;
};

const emptyFilters: Filters = { tur: "", rol: "", kullanici: "", from: "", to: "" };

export function AdminIslemLogPage() {
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [applied, setApplied] = useState<Filters>(emptyFilters);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<IslemLogPage | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setBusy(true);
    setError("");
    api.islemLoglari({
      tur: applied.tur || undefined,
      rol: applied.rol || undefined,
      kullanici: applied.kullanici.trim() || undefined,
      from: applied.from || undefined,
      to: applied.to || undefined,
      page,
      size: 50
    })
      .then(setData)
      .catch((err) => setError(err instanceof ApiError ? err.message : "İşlem kayıtları alınamadı."))
      .finally(() => setBusy(false));
  }, [applied, page]);

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setApplied(filters);
  }

  function resetFilters() {
    setFilters(emptyFilters);
    setApplied(emptyFilters);
    setPage(0);
  }

  return (
    <Shell home="/admin">
      <PageHeader title="İşlem günlüğü" description="Sistemde yapılan önemli işlemlerin denetim kaydı." />
      <p style={{ color: "var(--muted)", maxWidth: 860, lineHeight: 1.55 }}>
        Sistemde yapılan önemli işlemler burada listelenir: girişler, başvuru kararları, dönem işlemleri,
        dağıtımlar ve birim takip kayıtları.
      </p>

      <form className="filter-bar" onSubmit={applyFilters}>
        <label>
          İşlem türü
          <select value={filters.tur} onChange={(e) => setFilters({ ...filters, tur: e.target.value as IslemTuru | "" })}>
            <option value="">Tümü</option>
            {TUR_OPTIONS.map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
        <label>
          Rol
          <select value={filters.rol} onChange={(e) => setFilters({ ...filters, rol: e.target.value as Role | "" })}>
            <option value="">Tümü</option>
            <option value="ADMIN">Yönetici</option>
            <option value="STUDENT">Öğrenci</option>
            <option value="BIRIM">Birim</option>
          </select>
        </label>
        <label>
          Kullanıcı
          <input
            value={filters.kullanici}
            onChange={(e) => setFilters({ ...filters, kullanici: e.target.value })}
            placeholder="Kullanıcı adı veya ad soyad"
          />
        </label>
        <label>
          Başlangıç
          <input type="date" value={filters.from} onChange={(e) => setFilters({ ...filters, from: e.target.value })} />
        </label>
        <label>
          Bitiş
          <input type="date" value={filters.to} onChange={(e) => setFilters({ ...filters, to: e.target.value })} />
        </label>
        <div className="filter-actions">
          <button type="submit" className="btn btn-primary" disabled={busy}>Filtrele</button>
          <button type="button" className="btn btn-secondary" onClick={resetFilters} disabled={busy}>Temizle</button>
        </div>
      </form>

      {error && <p className="error">{error}</p>}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Zaman</th>
              <th>Rol</th>
              <th>Kullanıcı</th>
              <th>İşlem</th>
              <th>Açıklama</th>
              <th>Detay</th>
            </tr>
          </thead>
          <tbody>
            {(data?.kayitlar ?? []).map((row: IslemLog) => (
              <tr key={row.id}>
                <td>{formatDate(row.zaman)}</td>
                <td>{ROL_LABELS[row.rol]}</td>
                <td>
                  <div>{row.kullaniciAdi}</div>
                  {row.adSoyad && row.adSoyad !== row.kullaniciAdi && (
                    <div style={{ color: "var(--muted)", fontSize: "0.9em" }}>{row.adSoyad}</div>
                  )}
                </td>
                <td>{TUR_LABELS[row.tur] ?? row.tur}</td>
                <td>{row.aciklama}</td>
                <td style={{ maxWidth: 280, whiteSpace: "pre-wrap" }}>{row.detay || "—"}</td>
              </tr>
            ))}
            {!busy && (data?.kayitlar.length ?? 0) === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: "center", color: "var(--muted)" }}>
                  Kayıt bulunamadı.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="filter-actions" style={{ marginTop: 16 }}>
          <button className="btn btn-secondary" disabled={busy || page <= 0} onClick={() => setPage((p) => p - 1)}>
            Önceki
          </button>
          <span style={{ alignSelf: "center", color: "var(--muted)" }}>
            Sayfa {data.page + 1} / {data.totalPages} ({data.total} kayıt)
          </span>
          <button
            className="btn btn-secondary"
            disabled={busy || page >= data.totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Sonraki
          </button>
        </div>
      )}
    </Shell>
  );
}
