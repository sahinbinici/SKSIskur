export function FilterChips({ items }: { items: { label: string; onClear?: () => void }[] }) {
  const active = items.filter((item) => item.label);
  if (active.length === 0) return null;
  return (
    <div className="filter-chips no-print" aria-label="Aktif filtreler">
      {active.map((item) => (
        <span key={item.label} className="filter-chip">
          {item.label}
          {item.onClear && (
            <button type="button" className="filter-chip-clear" onClick={item.onClear} aria-label={`${item.label} filtresini kaldır`}>
              ×
            </button>
          )}
        </span>
      ))}
    </div>
  );
}
