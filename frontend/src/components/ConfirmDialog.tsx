import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";

export type ConfirmOptions = {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "primary" | "danger";
};

type PendingConfirm = ConfirmOptions & { resolve: (value: boolean) => void };

const ConfirmContext = createContext<(options: ConfirmOptions) => Promise<boolean>>(() =>
  Promise.resolve(window.confirm("Devam edilsin mi?"))
);

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<PendingConfirm | null>(null);
  const confirmButtonRef = useRef<HTMLButtonElement>(null);

  const confirm = useCallback((options: ConfirmOptions) =>
    new Promise<boolean>((resolve) => setPending({ ...options, resolve })), []);

  useEffect(() => {
    if (pending) confirmButtonRef.current?.focus();
  }, [pending]);

  function close(result: boolean) {
    pending?.resolve(result);
    setPending(null);
  }

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {pending && (
        <div className="confirm-overlay" role="presentation" onClick={() => close(false)}>
          <div
            className="confirm-dialog card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="confirm-title"
            onClick={(event) => event.stopPropagation()}
          >
            <h4 id="confirm-title" style={{ marginTop: 0 }}>{pending.title}</h4>
            <p style={{ color: "var(--muted)", lineHeight: 1.55 }}>{pending.message}</p>
            <div className="confirm-dialog-actions">
              <button type="button" className="btn btn-secondary" onClick={() => close(false)}>
                {pending.cancelLabel ?? "Vazgeç"}
              </button>
              <button
                ref={confirmButtonRef}
                type="button"
                className={pending.variant === "danger" ? "btn btn-danger" : "btn btn-primary"}
                onClick={() => close(true)}
              >
                {pending.confirmLabel ?? "Onayla"}
              </button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  );
}

export function useConfirm() {
  return useContext(ConfirmContext);
}
