import type { ReactNode } from "react";

export function PageHeader({
  title,
  description,
  actions
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="page-heading no-print">
      <div>
        <h3 className="section" style={{ margin: 0 }}>{title}</h3>
        {description && <p className="muted" style={{ margin: "6px 0 0" }}>{description}</p>}
      </div>
      {actions && <div className="row" style={{ margin: 0 }}>{actions}</div>}
    </div>
  );
}

export function ActionCard({
  title,
  description,
  meta,
  children
}: {
  title: string;
  description?: string;
  meta?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <section className="card action-card no-print">
      <div className="action-card-body">
        <div>
          <h4 style={{ margin: 0 }}>{title}</h4>
          {description && <p style={{ color: "var(--muted)", margin: "6px 0 0", maxWidth: "52ch" }}>{description}</p>}
          {meta && <div className="action-card-meta">{meta}</div>}
        </div>
        {children && <div className="action-card-actions">{children}</div>}
      </div>
    </section>
  );
}
