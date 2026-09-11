import { Injectable, signal } from '@angular/core';

/**
 * Aviso de cobro que manda el servidor (402: cuenta en solo lectura por falta de
 * pago). Lo llena el interceptor y lo muestra la barra de arriba de la app.
 */
@Injectable({ providedIn: 'root' })
export class BillingNoticeService {
  readonly message = signal('');

  show(message: string): void {
    this.message.set(message);
  }

  clear(): void {
    this.message.set('');
  }
}
