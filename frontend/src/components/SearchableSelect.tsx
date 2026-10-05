import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";

export type SearchableSelectOption = {
  value: string;
  label: string;
  hint?: string;
};

type Props = {
  value: string;
  onChange: (value: string) => void;
  options: SearchableSelectOption[];
  placeholder?: string;
  emptyLabel?: string;
};

function normalize(value: string) {
  return value.toLocaleLowerCase("tr-TR").trim();
}

export function SearchableSelect({
  value,
  onChange,
  options,
  placeholder = "Ara...",
  emptyLabel = "Sonuç yok"
}: Props) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);

  const selected = options.find((option) => option.value === value);
  const filtered = useMemo(() => {
    const q = normalize(query);
    if (!q) return options;
    return options.filter((option) =>
      [option.label, option.hint, option.value].some((part) => part && normalize(part).includes(q))
    );
  }, [options, query]);

  useEffect(() => {
    if (!open) return;
    function onDoc(event: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
        setQuery("");
      }
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  useEffect(() => {
    setActive(0);
  }, [query, open]);

  function choose(next: string) {
    onChange(next);
    setOpen(false);
    setQuery("");
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setOpen(true);
      setActive((index) => Math.min(index + 1, Math.max(filtered.length - 1, 0)));
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((index) => Math.max(index - 1, 0));
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      const option = filtered[active];
      if (option) choose(option.value);
      return;
    }
    if (event.key === "Escape") {
      setOpen(false);
      setQuery("");
    }
  }

  return (
    <div className={`search-select${open ? " open" : ""}`} ref={rootRef}>
      <input
        value={open ? query : selected?.label ?? ""}
        placeholder={placeholder}
        onFocus={() => {
          setOpen(true);
          setQuery("");
        }}
        onChange={(event) => {
          setOpen(true);
          setQuery(event.target.value);
        }}
        onKeyDown={onKeyDown}
        aria-expanded={open}
        aria-autocomplete="list"
        autoComplete="off"
      />
      {open && (
        <ul className="search-select-list" role="listbox">
          {filtered.map((option, index) => (
            <li key={option.value || "__all"}>
              <button
                type="button"
                className={index === active ? "active" : ""}
                role="option"
                aria-selected={option.value === value}
                onMouseDown={(event) => event.preventDefault()}
                onMouseEnter={() => setActive(index)}
                onClick={() => choose(option.value)}
              >
                <b>{option.label}</b>
                {option.hint ? <small>{option.hint}</small> : null}
              </button>
            </li>
          ))}
          {filtered.length === 0 && <li className="search-select-empty">{emptyLabel}</li>}
        </ul>
      )}
    </div>
  );
}
