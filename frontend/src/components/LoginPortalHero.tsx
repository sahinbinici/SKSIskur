import { useEffect, useState } from "react";
import { api } from "../api";
import { formatDate } from "./ui";
import type { YoneticiPanosuDuyuru } from "../types";

const PROGRAM_FACTS = [
  { label: "Program süresi", value: "10 ay" },
  { label: "Haftalık çalışma", value: "En fazla 22,5 saat" },
  { label: "Katılım", value: "Noter kurası + belge kontrolü" }
];

const ELIGIBILITY = [
  "T.C. vatandaşı ve 18 yaşını tamamlamış olmak",
  "İŞKUR'a kayıtlı olmak",
  "Devlet üniversitesi öğrencisi olmak",
  "Açık öğretim / uzaktan eğitim öğrencisi olmamak",
  "Kaydı dondurulmuş veya pasif durumda olmamak"
];

const WORK_AREAS = [
  "Kampüs altyapı ve bakım",
  "Sosyal ve kültürel faaliyetler",
  "Akademik ve idari faaliyetler",
  "Toplumsal hizmet",
  "Dijital dönüşüm"
];

export function LoginPortalHero() {
  const [duyurular, setDuyurular] = useState<YoneticiPanosuDuyuru[]>([]);

  useEffect(() => {
    api.ogrenciPortalDuyurulari().then(setDuyurular).catch(() => setDuyurular([]));
  }, []);

  return (
    <>
      <p>Sağlık Kültür ve Spor Daire Başkanlığı</p>
      <h2>İŞKUR Gençlik Programı Başvuru Portalı</h2>
      <p>
        Öğrenciler evraklarını yükleyip başvurularını gönderir. SKS yöneticileri başvuruları inceler,
        kesin kayıt listesini karşılaştırır ve birimlere dağıtır. Birim kullanıcıları atanan öğrencilerin
        EK-6 ve puantaj takibini yapar.
      </p>
      <a
        href="https://www.iskur.gov.tr/is-ariyorum/is-ve-meslek-bulma-yontemleri/iskur-genclik-programi/"
        className="login-portal-link"
        target="_blank"
        rel="noreferrer"
      >
        İŞKUR resmî sayfa →
      </a>

      <section className="login-portal-section">
        <h3>Program özeti</h3>
        <div className="login-portal-stat-grid">
          {PROGRAM_FACTS.map((item) => (
            <article className="login-portal-stat" key={item.label}>
              <span>{item.label}</span>
              <strong>{item.value}</strong>
            </article>
          ))}
        </div>
      </section>

      <section className="login-portal-section">
        <div className="login-portal-columns">
          <article className="login-portal-card">
            <h4>Kimler başvurabilir?</h4>
            <ul>
              {ELIGIBILITY.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </article>
          <article className="login-portal-card">
            <h4>Çalışma alanları</h4>
            <div className="login-portal-tags">
              {WORK_AREAS.map((item) => (
                <span className="login-portal-tag" key={item}>{item}</span>
              ))}
            </div>
          </article>
        </div>
      </section>

      <section className="login-portal-section">
        <h3>Duyurular</h3>
        {duyurular.length === 0 ? (
          <div className="login-portal-empty">Şu an yayında duyuru bulunmuyor.</div>
        ) : (
          <div className="login-duyurular">
            {duyurular.map((duyuru) => (
              <article className="login-duyuru" key={duyuru.id}>
                <strong>{duyuru.baslik}</strong>
                <p>{duyuru.mesaj}</p>
                <span className="login-duyuru-meta">
                  {formatDate(duyuru.guncellemeTarihi)} · {duyuru.gonderenAdmin}
                </span>
              </article>
            ))}
          </div>
        )}
      </section>

      <p className="login-portal-footer">
        Gaziantep Üniversitesi · Sağlık Kültür ve Spor Daire Başkanlığı
      </p>
    </>
  );
}
