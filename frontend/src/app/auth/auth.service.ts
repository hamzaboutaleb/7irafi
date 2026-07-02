import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';

interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

interface LoginRequest {
  email: string;
  password: string;
}

interface AuthResponse {
  accessToken: string;
}

const API = 'http://localhost:8080';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  register(data: RegisterRequest) {
    return this.http.post<AuthResponse>(`${API}/auth/register`, data);
  }

  login(data: LoginRequest) {
    return this.http.post<AuthResponse>(`${API}/auth/login`, data).pipe(
      tap((res) => localStorage.setItem('token', res.accessToken))
    );
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }
}
