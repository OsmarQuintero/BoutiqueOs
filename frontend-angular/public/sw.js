const SHELL_CACHE = 'boutiqueos-shell-v2';
const API_CACHE = 'boutiqueos-api-v2';
const SHELL_URL = './index.html';

// En localhost corre `ng serve`: ahi los archivos no llevan huella (main.js se
// llama igual en cada sesion) y guardarlos con cache-first dejaba la pagina en
// blanco al reiniciar el servidor. En desarrollo este service worker no hace nada.
const IS_LOCAL = ['localhost', '127.0.0.1'].includes(self.location.hostname);

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll([SHELL_URL]).catch(() => undefined)),
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key !== SHELL_CACHE && key !== API_CACHE)
            .map((key) => caches.delete(key)),
        ),
      ),
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  if (IS_LOCAL) {
    return;
  }

  const request = event.request;

  if (request.method !== 'GET') {
    return;
  }

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return;
  }

  // runtime-config.js trae la URL del backend y no lleva huella en el nombre:
  // con cache-first, un cambio de URL nunca les llegaba a quienes ya lo tenian.
  if (url.pathname.startsWith('/api/') || url.pathname.endsWith('/runtime-config.js')) {
    event.respondWith(networkFirst(request));
    return;
  }

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).catch(() => caches.match(SHELL_URL).then((cached) => cached || caches.match(request))),
    );
    return;
  }

  event.respondWith(cacheFirst(request));
});

async function networkFirst(request) {
  try {
    const response = await fetch(request);
    if (response && response.ok) {
      const clone = response.clone();
      const cache = await caches.open(API_CACHE);
      cache.put(request, clone);
    }
    return response;
  } catch (error) {
    const cached = await caches.match(request);
    if (cached) {
      return cached;
    }
    return Response.error();
  }
}

async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) {
    return cached;
  }
  const response = await fetch(request);
  if (response && response.ok) {
    const cache = await caches.open(SHELL_CACHE);
    cache.put(request, response.clone());
  }
  return response;
}

// Si un navegador todavia tiene la version vieja registrada en localhost, al
// actualizarse a esta borra todas sus caches y se da de baja sola.
if (IS_LOCAL) {
  self.addEventListener('activate', (event) => {
    event.waitUntil(
      caches
        .keys()
        .then((keys) => Promise.all(keys.map((key) => caches.delete(key))))
        .then(() => self.registration.unregister()),
    );
  });
}
