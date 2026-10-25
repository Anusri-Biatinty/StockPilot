import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { AuthResponse } from './types';

/**
 * Token storage: the short-lived access token lives only in this module's memory (never in
 * localStorage/sessionStorage), so injected scripts cannot read it from storage and it disappears when the tab
 * closes. The long-lived refresh token is an HttpOnly, SameSite=Strict cookie that JavaScript cannot read at
 * all; it is used to obtain a new access token on page load and whenever the current one expires.
 */
let accessToken: string | null = null;
let refreshTimer: ReturnType<typeof setTimeout> | undefined;
let refreshInFlight: Promise<AuthResponse | null> | null = null;

type SessionListener = (session: AuthResponse | null) => void;
const listeners = new Set<SessionListener>();

export const api = axios.create({ baseURL: '/api/v1', withCredentials: true });

export function onSessionChange(listener: SessionListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function applySession(session: AuthResponse | null): void {
  accessToken = session?.accessToken ?? null;
  clearTimeout(refreshTimer);
  if (session) {
    // Refresh one minute before expiry so active users never see a 401.
    const delay = Math.max(10, session.expiresIn - 60) * 1000;
    refreshTimer = setTimeout(() => void refreshSession(), delay);
  }
  listeners.forEach((l) => l(session));
}

/** Exchanges the refresh cookie for a new access token. Concurrent callers share one request. */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = axios
      .post<AuthResponse>('/api/v1/auth/refresh', null, { withCredentials: true })
      .then((res) => {
        applySession(res.data);
        return res.data;
      })
      .catch(() => {
        applySession(null);
        return null;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const isAuthCall = original?.url?.startsWith('/auth/');
    if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      const session = await refreshSession();
      if (session) {
        original.headers.Authorization = `Bearer ${session.accessToken}`;
        return api(original);
      }
    }
    return Promise.reject(error);
  },
);
