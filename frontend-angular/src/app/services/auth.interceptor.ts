import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { StoreService } from './store.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(StoreService);
  const isApiRequest = req.url.includes('/api/');
  const isLoginRequest = req.url.endsWith('/api/settings/login');
  const isPasswordResetRequest =
    req.url.endsWith('/api/settings/password-reset/request') ||
    req.url.endsWith('/api/settings/password-reset/validate') ||
    req.url.endsWith('/api/settings/password-reset/confirm');
  const isPublicOnboardingRequest =
    req.url.endsWith('/api/onboarding/start') || req.url.endsWith('/api/onboarding/complete');
  const isPublicRequest = isLoginRequest || isPasswordResetRequest || isPublicOnboardingRequest;

  let request = req;
  if (isApiRequest && !isPublicRequest) {
    const token = store.currentSessionToken();
    if (token) {
      request = req.clone({
        setHeaders: {
          'X-Boutique-Session': token,
        },
      });
    }
  }

  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        isApiRequest &&
        !isPublicRequest &&
        error instanceof HttpErrorResponse &&
        error.status === 401
      ) {
        store.handleSessionExpired();
      }
      return throwError(() => error);
    }),
  );
};
