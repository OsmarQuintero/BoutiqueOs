import { computed, Injectable, signal } from '@angular/core';
import { finalize, Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class RefreshService {
  private readonly depth = signal(0);
  private readonly text = signal('');
  private failsafeTimer: ReturnType<typeof setTimeout> | null = null;

  readonly isActive = computed(() => this.depth() > 0);
  readonly message = computed(() => this.text() || 'Actualizando...');

  track<T>(message: string, request: Observable<T>): Observable<T> {
    this.show(message);
    return request.pipe(finalize(() => this.hide()));
  }

  flash(message: string, duration = 350): void {
    this.show(message);
    window.setTimeout(() => this.hide(), duration);
  }

  show(message: string): void {
    this.depth.update((value) => value + 1);
    this.text.set(message);
    this.resetFailsafe();
  }

  hide(): void {
    this.depth.update((value) => Math.max(value - 1, 0));
    if (this.depth() === 0) {
      this.text.set('');
      this.clearFailsafe();
    }
  }

  reset(): void {
    this.depth.set(0);
    this.text.set('');
    this.clearFailsafe();
  }

  private resetFailsafe(): void {
    this.clearFailsafe();
    this.failsafeTimer = setTimeout(() => this.reset(), 8000);
  }

  private clearFailsafe(): void {
    if (this.failsafeTimer) {
      clearTimeout(this.failsafeTimer);
      this.failsafeTimer = null;
    }
  }
}
