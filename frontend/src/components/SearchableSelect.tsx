import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";

export type SearchableSelectOption = {
  value: string;
  label: string;
  hint?: string;
};

type BaseProps = {
  options: SearchableSelectOption[];
  placeholder?: string;
  emptyLabel?: string;
};

type SingleProps = BaseProps & {
  multiple?: false;
  value: string;
  onChange: (value: string) => void;
};

type MultiProps = BaseProps & {
  multiple: true;
  values: string[];
  onChange: (values: string[]) => void;
};

type Props = SingleProps | MultiProps;

function normalize(value: string) {
  return value.toLocaleLowerCase("tr-TR").trim();
}

export function SearchableSelect(props: Props) {
  const { options, placeholder = "Ara...", emptyLabel = "Sonuç yok" } = props;
  const multiple = props.multiple === true;
  const selectedValues = multiple ? props.values : props.value ? [props.value] : [];
  const rootRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);

  const selectedOptions = options.filter((option) => selectedValues.includes(option.value));
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

  function commit(next: string[]) {
    if (multiple) {
      props.onChange(next);
      return;
    }
    props.onChange(next[0] ?? "");
    setOpen(false);
    setQuery("");
  }

  function toggle(value: string) {
    if (!multiple) {
      commit([value]);
      return;
    }
    commit(selectedValues.includes(value)
      ? selectedValues.filter((item) => item !== value)
      : [...selectedValues, value]);
  }

  function selectFiltered() {
    const next = new Set(selectedValues);
    filtered.forEach((option) => next.add(option.value));
    commit([...next]);
  }

  function displayValue() {
    if (open) return query;
    if (selectedOptions.length === 0) return "";
    if (!multiple) return selectedOptions[0]?.label ?? "";
    if (selectedOptions.length === 1) return selectedOptions[0].label;
    if (selectedOptions.length === options.length && options.length > 0) return `Tüm birimler (${options.length})`;
    return `${selectedOptions.length} birim seçildi`;
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
      if (option) toggle(option.value);
      return;
    }
    if (event.key === "Escape") {
      setOpen(false);
      setQuery("");
    }
  }

  return (
    <div className={`search-select${open ? " open" : ""}${multiple ? " multi" : ""}`} ref={rootRef}>
      <input
        value={displayValue()}
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
        <ul className="search-select-list" role="listbox" aria-multiselectable={multiple}>
          {multiple && (
            <li className="search-select-actions">
              <button type="button" onMouseDown={(event) => event.preventDefault()} onClick={selectFiltered}>
                {query ? "Filtrelenenleri seç" : "Tümünü seç"}
              </button>
              <button type="button" onMouseDown={(event) => event.preventDefault()} onClick={() => commit([])}>
                Temizle
              </button>
            </li>
          )}
          {filtered.map((option, index) => {
            const checked = selectedValues.includes(option.value);
            return (
              <li key={option.value || "__all"}>
                <button
                  type="button"
                  className={`${index === active ? "active" : ""}${checked ? " selected" : ""}`}
                  role="option"
                  aria-selected={checked}
                  onMouseDown={(event) => event.preventDefault()}
                  onMouseEnter={() => setActive(index)}
                  onClick={() => toggle(option.value)}
                >
                  {multiple && <span className={`search-select-check${checked ? " on" : ""}`} aria-hidden="true" />}
                  <span>
                    <b>{option.label}</b>
                    {option.hint ? <small>{option.hint}</small> : null}
                  </span>
                </button>
              </li>
            );
          })}
          {filtered.length === 0 && <li className="search-select-empty">{emptyLabel}</li>}
        </ul>
      )}
    </div>
  );
}
