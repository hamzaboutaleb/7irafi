import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { ApiSuccess } from '../types/api.js';
import { User } from '../types/user.js';

export interface AuthData {
  accessToken: string;
  user: User;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  password: string;
  email: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);

  login(email: string, password: string) {
    return this.http.post<ApiSuccess<AuthData>>('auth/login', { email, password });
  }

  register(registerRequest: RegisterRequest) {
    return this.http.post<ApiSuccess<AuthData>>('auth/register', registerRequest);
  }

  saveToken(token: string) {
    localStorage.setItem('token', token);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  logout() {
    localStorage.removeItem('token');
  }
}
