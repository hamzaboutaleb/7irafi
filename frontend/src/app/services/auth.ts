import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Presence } from './presence.js';

export interface AuthUser {
  id: string;
  email: string;
  role: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly revision = signal(0);
  readonly sessionRevision = this.revision.asReadonly();
  private http = inject(HttpClient);
  private presence = inject(Presence);

  constructor() {
    // The shared token may belong to a different tab's account. Require a fresh login.
    localStorage.removeItem('token');
  }

  login(email: string, password: string) {
    return this.http.post<AuthResponse>('auth/login', { email, password });
  }

  register(registerRequest: RegisterRequest) {
    return this.http.post<AuthResponse>('auth/register', registerRequest);
  }

  saveToken(token: string) {
    // Keep each tab's account independent, including across page reloads.
    sessionStorage.setItem('token', token);
    this.revision.update(value => value + 1);
    this.presence.start();
  }

  getToken(): string | null {
    return sessionStorage.getItem('token');
  }

  /**
   * The signed-in user's id: the JWT subject. Read for display only ("is this
   * message mine?"); the server never trusts the browser's claim about it.
   */
  currentUserId(): string | null {
    const token = this.getToken();
    if (!token) return null;

    try {
      const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(payload)).sub ?? null;
    } catch {
      return null;
    }
  }

  logout() {
    sessionStorage.removeItem('token');
    this.revision.update(value => value + 1);
    this.presence.stop();
  }
}
