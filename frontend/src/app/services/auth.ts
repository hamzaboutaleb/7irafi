import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, defer, finalize, map, Observable, of, shareReplay, throwError } from 'rxjs';
import { Presence } from './presence.js';
import { NetworkService } from './network.js';
import { authDestination } from '../guards/auth-destination.js';

export interface AuthUser {
  id: string;
  email: string;
  role: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  // send it to auth/refresh to get a new pair when accessToken expires
  refreshToken: string;
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
  private network = inject(NetworkService);
  private router = inject(Router);
  // the refresh call running right now, shared by every request waiting for it
  private refreshInFlight: Observable<string> | null = null;

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

  // after login or register: a new user is signed in
  saveTokens(accessToken: string, refreshToken: string) {
    this.storeTokens(accessToken, refreshToken);
    this.revision.update(value => value + 1);
    this.presence.start();
  }

  // Keep each tab's account independent, including across page reloads.
  private storeTokens(accessToken: string, refreshToken: string) {
    sessionStorage.setItem('token', accessToken);
    sessionStorage.setItem('refreshToken', refreshToken);
  }

  // Access token expired: trade the refresh token for a new pair.
  // Many requests can fail at the same time, so they all share ONE refresh call.
  // Same user, so sessionRevision does not change (pages do not reload).
  refreshAccessToken(): Observable<string> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) return throwError(() => new Error('No refresh token'));

    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<AuthResponse>('auth/refresh', { refreshToken }).pipe(
        map((response) => {
          this.storeTokens(response.accessToken, response.refreshToken);
          return response.accessToken;
        }),
        // done (success or error): the next expiry starts a new refresh
        finalize(() => (this.refreshInFlight = null)),
        // every waiting request gets the same answer, the call is made once
        shareReplay(1),
      );
    }
    return this.refreshInFlight;
  }

  // After login or register: save the token, then open the page the user wanted.
  // Emits false if that page could not be opened, so the page can show an error.
  finishSignIn(response: AuthResponse, returnUrl: string | null): Observable<boolean> {
    // defer: nothing runs until someone subscribes (like an HTTP call)
    return defer(() => {
      this.saveTokens(response.accessToken, response.refreshToken);
      // authDestination only keeps returnUrl if it is inside our site, otherwise it uses "/".
      return this.router.navigateByUrl(authDestination(returnUrl));
    }).pipe(catchError(() => of(false)));
  }

  getToken(): string | null {
    return sessionStorage.getItem('token');
  }

  getRefreshToken(): string | null {
    return sessionStorage.getItem('refreshToken');
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
    // tell the server this refresh token is dead; the tab logs out even if this fails
    const refreshToken = this.getRefreshToken();
    if (refreshToken) {
      this.http.post('auth/logout', { refreshToken }).subscribe({ error: () => {} });
    }
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('refreshToken');
    this.revision.update(value => value + 1);
    this.presence.stop();
    // forget the old user's contacts
    this.network.clear();
  }
}
