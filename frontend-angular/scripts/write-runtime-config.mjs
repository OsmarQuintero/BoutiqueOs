import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const publicDir = join(process.cwd(), 'public');
const outputPath = join(publicDir, 'runtime-config.js');
const apiUrl = (process.env.BOUTIQUE_API_URL || '').trim().replace(/\/$/, '');

mkdirSync(publicDir, { recursive: true });

writeFileSync(
  outputPath,
  `window.__BOUTIQUE_API_URL__ = ${JSON.stringify(apiUrl)};\n`,
  'utf8'
);
