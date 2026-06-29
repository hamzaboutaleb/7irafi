import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../environments/environment.development.js';

export const baseUrlInterceptor: HttpInterceptorFn = (req, next) => {
  const apiReq = req.clone({ url: `${environment.apiUrl}/${req.url}` });
  return next(apiReq);
};
