import { isDevMode } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));

if ('serviceWorker' in navigator) {
  if (isDevMode()) {
    // En desarrollo el service worker estorba: guardaba main.js y las dependencias
    // de Vite con cache-first, y al reiniciar `ng serve` la pagina se quedaba en
    // blanco pidiendo modulos que ya no existian (504 Outdated Optimize Dep).
    // Si quedo alguno registrado de antes, se da de baja junto con su cache.
    navigator.serviceWorker
      .getRegistrations()
      .then((registrations) => registrations.forEach((registration) => registration.unregister()));
    if ('caches' in window) {
      caches
        .keys()
        .then((keys) => keys.filter((key) => key.startsWith('boutiqueos-')).forEach((key) => caches.delete(key)));
    }
  } else {
    window.addEventListener('load', () => {
      navigator.serviceWorker
        .register('./sw.js')
        .catch((err) => console.error('Service worker no se pudo registrar', err));
    });
  }
}
