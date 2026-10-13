"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { onSessionChange, refreshSession, setSession } from "@/lib/api/client";
import { authApi } from "@/lib/api/endpoints";
import type { UserProfile } from "@/types/api";

type Status = "loading" | "authenticated" | "anonymous";

interface AuthContextValue {
  status: Status;
  user: UserProfile | null;
  isAdmin: boolean;
  login: (email: string, password: string) => Promise<UserProfile>;
  register: (fullName: string, email: string, password: string) => Promise<UserProfile>;
  logout: () => Promise<void>;
  updateUser: (user: UserProfile) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Restores the session on load by exchanging the HttpOnly refresh cookie for an in-memory access
 * token. Roles shown here only drive navigation; the API enforces every permission.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>("loading");
  const [user, setUser] = useState<UserProfile | null>(null);

  useEffect(() => {
    const unsubscribe = onSessionChange((session) => {
      setUser(session?.user ?? null);
      setStatus(session ? "authenticated" : "anonymous");
    });
    refreshSession().then((session) => {
      if (!session) setStatus("anonymous");
    });
    return () => {
      unsubscribe();
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const session = await authApi.login(email, password);
    setSession(session);
    return session.user;
  }, []);

  const register = useCallback(async (fullName: string, email: string, password: string) => {
    const session = await authApi.register(fullName, email, password);
    setSession(session);
    return session.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setSession(null);
    }
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, isAdmin: user?.role === "ADMIN", login, register, logout, updateUser: setUser }),
    [status, user, login, register, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
