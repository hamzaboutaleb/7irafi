import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../environments/environment.js';
import { isApiRequest } from './api-request.js';

export const baseUrlInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApiRequest(req.url)) {
    return next(req);
  }
  const apiReq = req.clone({ url: `${environment.apiUrl}/${req.url}` });
  return next(apiReq);
};
