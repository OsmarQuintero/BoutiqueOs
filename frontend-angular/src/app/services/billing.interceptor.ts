import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { BillingNoticeService } from './billing-notice.service';

/**
 * Si el servidor responde 402 (pago vencido o sin suscripcion), se muestra el
 * motivo arriba en vez de que cada pantalla falle con un error generico.
 */
export const billingInterceptor: HttpInterceptorFn = (req, next) => {
  const notice = inject(BillingNoticeService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 402) {
        notice.show(error.error?.message || 'Tu suscripción no está activa.');
      }
      return throwError(() => error);
    }),
  );
};
