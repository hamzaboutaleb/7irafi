import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, TitleStrategy, withInMemoryScrolling } from '@angular/router';

import { PageTitleStrategy } from './services/page-title-strategy.js';

import { routes } from './app.routes';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { baseUrlInterceptor } from './interceptors/base-url-interceptor.js';
import { authInterceptor } from './interceptors/auth-interceptor.js';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      // Every new page opens at the top (Back still returns to where you were).
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),
    { provide: TitleStrategy, useClass: PageTitleStrategy },
    provideHttpClient(withInterceptors([authInterceptor, baseUrlInterceptor])),
  ],
};
