import { AuthResponse, AuthTokens, AuthUser } from '@/types/auth';
import { Rider, Session, RiderMetrics } from '@/types/sensor';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:3000';

const ACCESS_TOKEN_KEY = 'ridepulse_access_token';
const REFRESH_TOKEN_KEY = 'ridepulse_refresh_token';

type RequestOptions = RequestInit & {
  skipAuth?: boolean;
};

class ApiClient {
  private baseUrl: string;
  private isRefreshing = false;
  private refreshPromise: Promise<string | null> | null = null;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  private getAccessToken(): string | null {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  private getRefreshToken(): string | null {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  private setTokens(tokens: AuthTokens) {
    if (typeof window === 'undefined') return;
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  }

  clearTokens() {
    if (typeof window === 'undefined') return;
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }

  getStoredTokens(): { accessToken: string | null; refreshToken: string | null } {
    return {
      accessToken: this.getAccessToken(),
      refreshToken: this.getRefreshToken(),
    };
  }

  private async refreshAccessToken(): Promise<string | null> {
    if (this.isRefreshing && this.refreshPromise) {
      return this.refreshPromise;
    }

    const refreshToken = this.getRefreshToken();
    if (!refreshToken) return null;

    this.isRefreshing = true;
    this.refreshPromise = (async () => {
      try {
        const response = await fetch(`${this.baseUrl}/api/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });

        if (!response.ok) {
          this.clearTokens();
          return null;
        }

        const data = (await response.json()) as { accessToken: string };
        if (!data.accessToken) {
          this.clearTokens();
          return null;
        }

        if (typeof window !== 'undefined') {
          localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken);
        }

        return data.accessToken;
      } catch {
        this.clearTokens();
        return null;
      } finally {
        this.isRefreshing = false;
        this.refreshPromise = null;
      }
    })();

    return this.refreshPromise;
  }

  private async request<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
    const url = `${this.baseUrl}${endpoint}`;
    const headers = new Headers(options.headers || {});
    headers.set('Content-Type', 'application/json');

    if (!options.skipAuth) {
      const token = this.getAccessToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
    }

    let response = await fetch(url, { ...options, headers });

    if (response.status === 401 && !options.skipAuth) {
      const newAccessToken = await this.refreshAccessToken();
      if (newAccessToken) {
        headers.set('Authorization', `Bearer ${newAccessToken}`);
        response = await fetch(url, { ...options, headers });
      }
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || error.message || `HTTP ${response.status}`);
    }

    return response.json();
  }

  async login(email: string, password: string): Promise<AuthResponse> {
    const data = await this.request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      skipAuth: true,
    });
    this.setTokens(data.tokens);
    return data;
  }

  async register(input: {
    email: string;
    password: string;
    name: string;
    role: 'rider' | 'coach' | 'admin';
    teamId?: string;
  }): Promise<AuthResponse> {
    const data = await this.request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(input),
      skipAuth: true,
    });
    this.setTokens(data.tokens);
    return data;
  }

  async me(): Promise<AuthUser> {
    const data = await this.request<{ user: AuthUser }>('/api/auth/me');
    return data.user;
  }

  async getRiders(): Promise<Rider[]> {
    const data = await this.request<{ riders: Rider[] }>('/api/riders');
    return data.riders;
  }

  async getRider(id: string): Promise<Rider> {
    return this.request<Rider>(`/api/riders/${id}`);
  }

  async createRider(data: Partial<Rider>): Promise<Rider> {
    return this.request<Rider>('/api/riders', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getActiveSessions(): Promise<RiderMetrics[]> {
    return this.request<RiderMetrics[]>('/api/sessions/active');
  }

  async getSession(sessionId: string): Promise<RiderMetrics> {
    return this.request<RiderMetrics>(`/api/sessions/${sessionId}`);
  }

  async getSessionHistory(riderId: string, limit = 10): Promise<Session[]> {
    return this.request<Session[]>(`/api/riders/${riderId}/sessions?limit=${limit}`);
  }
}

export const apiClient = new ApiClient(API_URL);
