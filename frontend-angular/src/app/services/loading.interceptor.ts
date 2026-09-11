import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';
import { RefreshService } from './refresh.service';

/**
 * Enciende el indicador de carga con las peticiones que de verdad estan
 * corriendo, en vez de que cada pantalla lo prenda y lo apague a mano.
 *
 * Se excluye el sondeo en segundo plano: si el indicador parpadeara cada vez
 * que la app revisa la suscripcion o la salud del backend, dejaria de
 * significar algo.
 */
const SILENT_PATHS = ['/api/health', '/api/subscription'];

export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  const refresh = inject(RefreshService);

  const isApi = req.url.includes('/api/');
  const isSilent = SILENT_PATHS.some((path) => req.url.includes(path));

  if (!isApi || isSilent) {
    return next(req);
  }

  refresh.show();
  return next(req).pipe(finalize(() => refresh.hide()));
};
