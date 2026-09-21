import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
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
    this.presence.start();
  }

  getToken(): string | null {
    return sessionStorage.getItem('token');
  }

  logout() {
    sessionStorage.removeItem('token');
    this.presence.stop();
  }
}
