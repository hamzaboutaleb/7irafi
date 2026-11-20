import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.js';
import { Router } from '@angular/router';
import { isApiRequest} from './api-request.js';

// the same request, with this access token in the Authorization header
function withToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getToken();
  const apiRequest = isApiRequest(req.url);
  // these send no access token: it may be expired, and the gateway would refuse the call
  const publicAuthRequest = ['auth/login', 'auth/register', 'auth/refresh', 'auth/logout'].includes(req.url);
  const shouldAttachToken = Boolean(token) && apiRequest && !publicAuthRequest;

  const authenticatedRequest = shouldAttachToken ? withToken(req, token!) : req;

  return next(authenticatedRequest).pipe(
    catchError((err: HttpErrorResponse) => {
      // not "access token expired": let the page handle the error
      if (!shouldAttachToken || err.status !== 401) return throwError(() => err);

      const current = authService.getToken();
      // logged out meanwhile: nothing to retry
      if (!current) return throwError(() => err);
      // another request already got a new token while this one was waiting: just retry
      if (current !== token) return next(withToken(req, current));

      // get a new access token (shared with other failing requests), then retry once
      return authService.refreshAccessToken().pipe(
        catchError(() => {
          // refresh token expired or revoked: the user must log in again
          if (authService.getToken() === token) {
            authService.logout();
            router.navigate(['/login']);
          }
          return throwError(() => err);
        }),
        switchMap((newToken) => next(withToken(req, newToken))),
      );
    }),
  );
};
