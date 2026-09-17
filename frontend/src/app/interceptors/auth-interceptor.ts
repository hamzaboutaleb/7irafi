import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.js';
import { Router } from '@angular/router';
import { isApiRequest} from './api-request.js';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getToken();
  const apiRequest = isApiRequest(req.url);
  const publicAuthRequest = req.url === 'auth/login' || req.url === 'auth/register';
  const shouldAttachToken = Boolean(token) && apiRequest && !publicAuthRequest;

  const authenticatedRequest = shouldAttachToken
    ? req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`,
        },
      })
    : req;
  return next(authenticatedRequest).pipe(
    catchError((err: HttpErrorResponse) => {
      if (shouldAttachToken && err.status === 401 && authService.getToken() === token) {
        authService.logout();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    }),
  );
};
