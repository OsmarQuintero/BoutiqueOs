import { computed, Injectable, signal } from '@angular/core';
import { finalize, Observable } from 'rxjs';

/**
 * Estado de carga de la aplicacion.
 *
 * Dos reglas que evitan que la app se sienta lenta:
 *
 * - **Nada de demoras inventadas.** El indicador se enciende con peticiones
 *   reales (lo alimenta el interceptor HTTP), no con temporizadores fijos.
 * - **Las peticiones rapidas no muestran nada.** Si algo responde antes de
 *   GRACE_MS no se pinta indicador; parpadear es peor que no mostrar nada.
 *   Y si llega a mostrarse, se queda al menos MIN_VISIBLE_MS para que no
 *   aparezca y desaparezca de golpe.
 */
@Injectable({ providedIn: 'root' })
export class RefreshService {
  private static readonly GRACE_MS = 180;
  private static readonly MIN_VISIBLE_MS = 320;

  private readonly depth = signal(0);
  private readonly text = signal('');
  private readonly visibleState = signal(false);

  private graceTimer: ReturnType<typeof setTimeout> | null = null;
  private minVisibleTimer: ReturnType<typeof setTimeout> | null = null;
  private failsafeTimer: ReturnType<typeof setTimeout> | null = null;
  private shownAt = 0;

  /** Hay peticiones en vuelo (aunque todavia no se pinte nada). */
  readonly isActive = computed(() => this.depth() > 0);

  /** El indicador debe verse en pantalla. */
  readonly isVisible = computed(() => this.visibleState());

  readonly message = computed(() => this.text() || 'Actualizando');

  /**
   * Envuelve una peticion y le pone texto propio al indicador. El interceptor
   * ya cuenta todas las peticiones; esto se usa donde vale la pena decir que
   * esta pasando ("Guardando venta") en lugar del texto generico.
   */
  track<T>(message: string, request: Observable<T>): Observable<T> {
    this.show(message);
    return request.pipe(finalize(() => this.hide()));
  }

  show(message?: string): void {
    if (message) {
      this.text.set(message);
    }
    this.depth.update((value) => value + 1);

    if (this.graceTimer === null && !this.visibleState()) {
      this.graceTimer = setTimeout(() => {
        this.graceTimer = null;
        if (this.depth() > 0) {
          this.visibleState.set(true);
          this.shownAt = Date.now();
        }
      }, RefreshService.GRACE_MS);
    }

    this.armFailsafe();
  }

  hide(): void {
    this.depth.update((value) => Math.max(value - 1, 0));
    if (this.depth() > 0) {
      return;
    }

    this.clearTimer('grace');
    this.clearTimer('failsafe');

    if (!this.visibleState()) {
      this.text.set('');
      return;
    }

    const shownFor = Date.now() - this.shownAt;
    const remaining = Math.max(RefreshService.MIN_VISIBLE_MS - shownFor, 0);
    this.clearTimer('minVisible');
    this.minVisibleTimer = setTimeout(() => {
      this.minVisibleTimer = null;
      if (this.depth() === 0) {
        this.visibleState.set(false);
        this.text.set('');
      }
    }, remaining);
  }

  reset(): void {
    this.depth.set(0);
    this.visibleState.set(false);
    this.text.set('');
    this.clearTimer('grace');
    this.clearTimer('minVisible');
    this.clearTimer('failsafe');
  }

  /** Si una peticion se cuelga, el indicador no se queda encendido para siempre. */
  private armFailsafe(): void {
    this.clearTimer('failsafe');
    this.failsafeTimer = setTimeout(() => this.reset(), 15000);
  }

  private clearTimer(which: 'grace' | 'minVisible' | 'failsafe'): void {
    const timer =
      which === 'grace'
        ? this.graceTimer
        : which === 'minVisible'
          ? this.minVisibleTimer
          : this.failsafeTimer;

    if (timer === null) {
      return;
    }
    clearTimeout(timer);

    if (which === 'grace') this.graceTimer = null;
    else if (which === 'minVisible') this.minVisibleTimer = null;
    else this.failsafeTimer = null;
  }
}
