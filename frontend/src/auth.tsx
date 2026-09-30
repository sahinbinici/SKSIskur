import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { api, setToken } from "./api";
import type { AdminRole, AuthResponse, Role } from "./types";

type Session = {
  token: string;
  role: Role;
  displayName: string;
  ogrenciNo: string | null;
  birimKodu: string | null;
  birimAdi: string | null;
  username: string | null;
  userId: number | null;
  adminRole: AdminRole | null;
};

type AuthContextValue = {
  session: Session | null;
  loginStudent: (ogrenciNo: string, sifre: string) => Promise<void>;
  loginAdmin: (username: string, password: string) => Promise<void>;
  loginUnit: (username: string, password: string) => Promise<void>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);
const SESSION_KEY = "sksiskur.session";

function readSession(): Session | null {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Session;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => {
    const existing = readSession();
    if (existing?.token) {
      setToken(existing.token);
    }
    return existing;
  });

  const value = useMemo<AuthContextValue>(() => ({
    session,
    loginStudent: async (ogrenciNo, sifre) => {
      const response = await api.studentLogin(ogrenciNo, sifre);
      persist(response, setSession);
    },
    loginAdmin: async (username, password) => {
      const response = await api.adminLogin(username, password);
      persist(response, setSession);
    },
    loginUnit: async (username, password) => {
      const response = await api.unitLogin(username, password);
      persist(response, setSession);
    },
    logout: () => {
      setToken(null);
      localStorage.removeItem(SESSION_KEY);
      setSession(null);
    }
  }), [session]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function persist(response: AuthResponse, setSession: (session: Session) => void) {
  const next: Session = {
    token: response.token,
    role: response.role,
    displayName: response.displayName,
    ogrenciNo: response.ogrenciNo,
    birimKodu: response.birimKodu,
    birimAdi: response.birimAdi,
    username: response.username,
    userId: response.userId,
    adminRole: response.adminRole
  };
  setToken(response.token);
  localStorage.setItem(SESSION_KEY, JSON.stringify(next));
  setSession(next);
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("AuthProvider eksik");
  }
  return ctx;
}
