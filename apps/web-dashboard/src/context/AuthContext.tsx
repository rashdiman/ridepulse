'use client';

import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { apiClient } from '@/lib/api';
import { AuthUser } from '@/types/auth';

type RegisterInput = {
  email: string;
  password: string;
  name: string;
  role: 'rider' | 'coach' | 'admin';
  teamId?: string;
};

type AuthContextType = {
  user: AuthUser | null;
  loading: boolean;
  isAuthenticated: boolean;
  accessToken: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
};

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [accessToken, setAccessToken] = useState<string | null>(null);

  const syncAccessToken = () => {
    const { accessToken: token } = apiClient.getStoredTokens();
    setAccessToken(token);
  };

  const bootstrap = async () => {
    const { accessToken: token, refreshToken } = apiClient.getStoredTokens();
    if (!token && !refreshToken) {
      setLoading(false);
      return;
    }

    try {
      const me = await apiClient.me();
      setUser(me);
      syncAccessToken();
    } catch {
      apiClient.clearTokens();
      setUser(null);
      setAccessToken(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void bootstrap();
  }, []);

  const login = async (email: string, password: string) => {
    const result = await apiClient.login(email, password);
    setUser(result.user);
    syncAccessToken();
  };

  const register = async (input: RegisterInput) => {
    const result = await apiClient.register(input);
    setUser(result.user);
    syncAccessToken();
  };

  const logout = () => {
    apiClient.clearTokens();
    setUser(null);
    setAccessToken(null);
  };

  const refreshUser = async () => {
    const me = await apiClient.me();
    setUser(me);
    syncAccessToken();
  };

  const value = useMemo<AuthContextType>(
    () => ({
      user,
      loading,
      isAuthenticated: !!user,
      accessToken,
      login,
      register,
      logout,
      refreshUser,
    }),
    [user, loading, accessToken]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
