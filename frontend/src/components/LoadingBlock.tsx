export function LoadingBlock({ label = "Yükleniyor..." }: { label?: string }) {
  return (
    <div className="loading-block" aria-live="polite">
      <div className="loading-skeleton" />
      <div className="loading-skeleton short" />
      <span>{label}</span>
    </div>
  );
}
