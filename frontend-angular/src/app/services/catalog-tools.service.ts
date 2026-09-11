import { Injectable } from '@angular/core';

/**
 * Herramientas del catalogo que no necesitan al servidor: leer hojas de Excel/CSV
 * para importar y armar el PDF de etiquetas con codigo de barras.
 *
 * El .xlsx es un zip con XML adentro. Se lee con DecompressionStream del propio
 * navegador, sin librerias: la unica conocida (SheetJS) en npm esta abandonada y
 * con vulnerabilidades.
 */

export interface ImportPreviewRow {
  line: number;
  name: string;
  category: string;
  size: string;
  color: string;
  sku: string;
  costPrice: number | null;
  salePrice: number | null;
  stock: number | null;
  minStock: number | null;
  styleCode: string;
  problem: string | null;
}

export interface LabelItem {
  name: string;
  variant: string;
  price: string;
  code: string;
  copies: number;
}

type ImportField = Exclude<keyof ImportPreviewRow, 'line' | 'problem'>;

const HEADER_ALIASES: Record<string, ImportField> = {
  nombre: 'name',
  producto: 'name',
  articulo: 'name',
  descripcion: 'name',
  name: 'name',
  categoria: 'category',
  category: 'category',
  departamento: 'category',
  talla: 'size',
  size: 'size',
  medida: 'size',
  color: 'color',
  colour: 'color',
  sku: 'sku',
  codigo: 'sku',
  'codigo de barras': 'sku',
  clave: 'sku',
  code: 'sku',
  barcode: 'sku',
  costo: 'costPrice',
  cost: 'costPrice',
  'precio costo': 'costPrice',
  'precio de costo': 'costPrice',
  'precio de compra': 'costPrice',
  'costo unitario': 'costPrice',
  precio: 'salePrice',
  'precio venta': 'salePrice',
  'precio de venta': 'salePrice',
  price: 'salePrice',
  pvp: 'salePrice',
  stock: 'stock',
  existencia: 'stock',
  existencias: 'stock',
  cantidad: 'stock',
  inventario: 'stock',
  piezas: 'stock',
  minimo: 'minStock',
  'stock minimo': 'minStock',
  'avisar con': 'minStock',
  min: 'minStock',
  modelo: 'styleCode',
  estilo: 'styleCode',
  style: 'styleCode',
};

const NUMBER_FIELDS: ImportField[] = ['costPrice', 'salePrice', 'stock', 'minStock'];

@Injectable({ providedIn: 'root' })
export class CatalogToolsService {
  // ---------------------------------------------------------------- importar

  async readSpreadsheet(file: File): Promise<string[][]> {
    const lower = file.name.toLowerCase();
    if (lower.endsWith('.xls')) {
      throw new Error('xls');
    }
    if (lower.endsWith('.xlsx')) {
      return this.readXlsx(file);
    }
    return this.parseCsv(await file.text());
  }

  /** Convierte la tabla en renglones de producto y marca los que tienen problema. */
  toImportRows(table: string[][]): { rows: ImportPreviewRow[]; missing: string[] } {
    const headerIndex = table.findIndex((row) => row.some((cell) => cell.trim()));
    if (headerIndex < 0) {
      return { rows: [], missing: ['nombre', 'precio'] };
    }
    const columns = new Map<ImportField, number>();
    table[headerIndex].forEach((cell, index) => {
      const field = HEADER_ALIASES[this.normalizeHeader(cell)];
      if (field && !columns.has(field)) {
        columns.set(field, index);
      }
    });
    const missing: string[] = [];
    if (!columns.has('name')) missing.push('nombre');
    if (!columns.has('salePrice')) missing.push('precio');
    if (missing.length) {
      return { rows: [], missing };
    }

    const rows: ImportPreviewRow[] = [];
    for (let i = headerIndex + 1; i < table.length; i++) {
      const cells = table[i];
      if (!cells.some((cell) => cell.trim())) continue;
      const read = (field: ImportField) => {
        const index = columns.get(field);
        return index === undefined ? '' : (cells[index] ?? '').trim();
      };
      const row: ImportPreviewRow = {
        line: i + 1,
        name: read('name'),
        category: read('category'),
        size: read('size'),
        color: read('color'),
        sku: read('sku'),
        costPrice: null,
        salePrice: null,
        stock: null,
        minStock: null,
        styleCode: read('styleCode'),
        problem: null,
      };
      for (const field of NUMBER_FIELDS) {
        const raw = read(field);
        if (!raw) continue;
        const value = this.parseNumber(raw);
        if (value === null) {
          row.problem = `"${raw}" no es un número`;
          continue;
        }
        const integer = field === 'stock' || field === 'minStock';
        (row[field] as number | null) = integer ? Math.round(value) : Math.round(value * 100) / 100;
      }
      if (!row.problem) {
        if (!row.name) row.problem = 'Falta el nombre';
        else if (row.salePrice === null || row.salePrice < 0) row.problem = 'Falta el precio de venta';
        else if ((row.stock ?? 0) < 0) row.problem = 'El stock no puede ser negativo';
      }
      rows.push(row);
    }
    return { rows, missing };
  }

  templateCsv(): string {
    return (
      '﻿' +
      [
        'nombre,categoria,talla,color,sku,costo,precio,stock,minimo,modelo',
        'Blusa de lino,Blusas,M,Blanco,,150,399,5,2,BLUL',
        'Vestido midi,Vestidos,CH,Negro,VES-001,300,799,3,1,',
      ].join('\r\n')
    );
  }

  parseCsv(text: string): string[][] {
    const clean = text.replace(/^﻿/, '');
    const firstLine = clean.split(/\r?\n/, 1)[0] ?? '';
    const delimiter = [';', '\t', ','].reduce((best, candidate) =>
      firstLine.split(candidate).length > firstLine.split(best).length ? candidate : best,
    ',');
    const rows: string[][] = [];
    let row: string[] = [];
    let cell = '';
    let quoted = false;
    for (let i = 0; i < clean.length; i++) {
      const char = clean[i];
      if (quoted) {
        if (char === '"' && clean[i + 1] === '"') {
          cell += '"';
          i++;
        } else if (char === '"') {
          quoted = false;
        } else {
          cell += char;
        }
      } else if (char === '"') {
        quoted = true;
      } else if (char === delimiter) {
        row.push(cell);
        cell = '';
      } else if (char === '\n' || char === '\r') {
        if (char === '\r' && clean[i + 1] === '\n') i++;
        row.push(cell);
        rows.push(row);
        row = [];
        cell = '';
      } else {
        cell += char;
      }
    }
    if (cell || row.length) {
      row.push(cell);
      rows.push(row);
    }
    return rows;
  }

  /** "$1,250.50", "1.250,50" o "399" -> numero; lo que no se entienda -> null. */
  parseNumber(raw: string): number | null {
    let text = raw.replace(/[$\s]/g, '');
    if (text.includes(',') && text.includes('.')) {
      text = text.lastIndexOf(',') > text.lastIndexOf('.')
        ? text.replace(/\./g, '').replace(',', '.')
        : text.replace(/,/g, '');
    } else if (/^\d{1,3}(,\d{3})+$/.test(text)) {
      text = text.replace(/,/g, '');
    } else {
      text = text.replace(',', '.');
    }
    if (!/^-?\d+(\.\d+)?$/.test(text)) return null;
    return Number(text);
  }

  private normalizeHeader(value: string): string {
    return value
      .normalize('NFD')
      .replace(/\p{M}/gu, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, ' ')
      .trim();
  }

  private async readXlsx(file: File): Promise<string[][]> {
    const entries = this.unzip(new Uint8Array(await file.arrayBuffer()));
    const text = async (name: string) => {
      const entry = entries.get(name);
      return entry ? this.inflate(entry) : null;
    };
    const parser = new DOMParser();
    const sharedXml = await text('xl/sharedStrings.xml');
    const shared: string[] = [];
    if (sharedXml) {
      const doc = parser.parseFromString(sharedXml, 'application/xml');
      for (const si of Array.from(doc.getElementsByTagName('si'))) {
        shared.push(Array.from(si.getElementsByTagName('t')).map((t) => t.textContent ?? '').join(''));
      }
    }
    const sheetXml = (await text(await this.firstSheetPath(entries, text))) ?? (await text('xl/worksheets/sheet1.xml'));
    if (!sheetXml) {
      throw new Error('xlsx');
    }
    const sheet = parser.parseFromString(sheetXml, 'application/xml');
    const rows: string[][] = [];
    for (const rowEl of Array.from(sheet.getElementsByTagName('row'))) {
      const rowIndex = Number(rowEl.getAttribute('r') || rows.length + 1) - 1;
      const row: string[] = [];
      for (const cellEl of Array.from(rowEl.getElementsByTagName('c'))) {
        const ref = cellEl.getAttribute('r') || '';
        const column = this.columnIndex(ref.replace(/\d+/g, ''));
        const type = cellEl.getAttribute('t');
        const v = cellEl.getElementsByTagName('v')[0]?.textContent ?? '';
        let value = v;
        if (type === 's') value = shared[Number(v)] ?? '';
        else if (type === 'inlineStr') {
          value = Array.from(cellEl.getElementsByTagName('t')).map((t) => t.textContent ?? '').join('');
        } else if (type === 'b') value = v === '1' ? 'VERDADERO' : 'FALSO';
        while (row.length < column) row.push('');
        row[column] = value;
      }
      while (rows.length < rowIndex) rows.push([]);
      rows[rowIndex] = row;
    }
    return rows;
  }

  private async firstSheetPath(
    entries: Map<string, { method: number; data: Uint8Array<ArrayBuffer> }>,
    text: (name: string) => Promise<string | null>,
  ): Promise<string> {
    const fallback = 'xl/worksheets/sheet1.xml';
    const workbook = await text('xl/workbook.xml');
    const rels = await text('xl/_rels/workbook.xml.rels');
    if (!workbook || !rels) return fallback;
    const parser = new DOMParser();
    const sheet = parser.parseFromString(workbook, 'application/xml').getElementsByTagName('sheet')[0];
    const id = sheet?.getAttribute('r:id');
    const rel = Array.from(parser.parseFromString(rels, 'application/xml').getElementsByTagName('Relationship'))
      .find((r) => r.getAttribute('Id') === id);
    const target = rel?.getAttribute('Target');
    if (!target) return fallback;
    const path = target.startsWith('/') ? target.slice(1) : `xl/${target}`;
    return entries.has(path) ? path : fallback;
  }

  private columnIndex(letters: string): number {
    let index = 0;
    for (const letter of letters.toUpperCase()) {
      index = index * 26 + (letter.charCodeAt(0) - 64);
    }
    return Math.max(index - 1, 0);
  }

  /** Lee el directorio central del zip: nombre, metodo y datos comprimidos de cada archivo. */
  private unzip(bytes: Uint8Array<ArrayBuffer>): Map<string, { method: number; data: Uint8Array<ArrayBuffer> }> {
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    let end = -1;
    for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
      if (view.getUint32(i, true) === 0x06054b50) {
        end = i;
        break;
      }
    }
    if (end < 0) {
      throw new Error('zip');
    }
    const count = view.getUint16(end + 10, true);
    let offset = view.getUint32(end + 16, true);
    const decoder = new TextDecoder();
    const entries = new Map<string, { method: number; data: Uint8Array<ArrayBuffer> }>();
    for (let n = 0; n < count; n++) {
      if (view.getUint32(offset, true) !== 0x02014b50) break;
      const method = view.getUint16(offset + 10, true);
      const compressedSize = view.getUint32(offset + 20, true);
      const nameLength = view.getUint16(offset + 28, true);
      const extraLength = view.getUint16(offset + 30, true);
      const commentLength = view.getUint16(offset + 32, true);
      const localOffset = view.getUint32(offset + 42, true);
      const name = decoder.decode(bytes.subarray(offset + 46, offset + 46 + nameLength));
      const localNameLength = view.getUint16(localOffset + 26, true);
      const localExtraLength = view.getUint16(localOffset + 28, true);
      const start = localOffset + 30 + localNameLength + localExtraLength;
      entries.set(name, { method, data: bytes.slice(start, start + compressedSize) });
      offset += 46 + nameLength + extraLength + commentLength;
    }
    return entries;
  }

  private async inflate(entry: { method: number; data: Uint8Array<ArrayBuffer> }): Promise<string> {
    if (entry.method === 0) {
      return new TextDecoder().decode(entry.data);
    }
    const stream = new Blob([entry.data]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
    return new Response(stream).text();
  }

  // ---------------------------------------------------------------- etiquetas

  /**
   * Etiquetas con codigo de barras (CODE128 del SKU): rollo termico de 50x25 mm,
   * una por pagina, u hoja carta de 30 (3x10, medida tipo 5160).
   */
  async buildLabels(items: LabelItem[], format: 'roll' | 'sheet', storeName: string): Promise<void> {
    const [{ jsPDF }, barcodeModule] = await Promise.all([import('jspdf'), import('jsbarcode')]);
    const JsBarcode = barcodeModule.default;
    // Cada barra minima mide 2 px en el canvas: el ancho dice cuantos modulos tiene el codigo.
    const images = new Map<string, { url: string; modules: number }>();
    const barcode = (code: string) => {
      let image = images.get(code);
      if (!image) {
        const canvas = document.createElement('canvas');
        JsBarcode(canvas, code, { format: 'CODE128', width: 2, height: 60, displayValue: false, margin: 0 });
        image = { url: canvas.toDataURL('image/png'), modules: canvas.width / 2 };
        images.set(code, image);
      }
      return image;
    };

    const sheet = format === 'sheet';
    const doc = sheet
      ? new jsPDF({ unit: 'mm', format: 'letter' })
      : new jsPDF({ unit: 'mm', format: [50, 25], orientation: 'landscape' });
    const width = sheet ? 66.7 : 50;
    const height = sheet ? 25.4 : 25;
    let index = 0;
    for (const item of items) {
      for (let copy = 0; copy < item.copies; copy++) {
        let x = 0;
        let y = 0;
        if (sheet) {
          const slot = index % 30;
          if (index > 0 && slot === 0) doc.addPage('letter', 'portrait');
          x = 4.8 + (slot % 3) * 69.85;
          y = 12.7 + Math.floor(slot / 3) * 25.4;
        } else if (index > 0) {
          doc.addPage([50, 25], 'landscape');
        }

        const pad = 2;
        doc.setTextColor(90, 90, 90);
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(5.5);
        doc.text((doc.splitTextToSize(storeName, width * 0.55) as string[])[0] ?? '', x + pad, y + pad + 1.8);
        doc.setTextColor(0, 0, 0);
        doc.setFont('helvetica', 'bold');
        doc.setFontSize(9.5);
        doc.text(item.price, x + width - pad, y + pad + 2.6, { align: 'right' });
        doc.setFontSize(6.5);
        doc.text((doc.splitTextToSize(item.name, width - pad * 2) as string[])[0] ?? '', x + pad, y + pad + 6.4);
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(6);
        if (item.variant) doc.text(item.variant, x + pad, y + pad + 9);
        // Barra minima de ~0.26 mm (2 puntos de una termica de 203 dpi), centrado y con
        // margen blanco a los lados: estirar un codigo corto a todo el ancho lo vuelve
        // dificil de leer para algunos lectores.
        const code = barcode(item.code);
        const barWidth = Math.min(code.modules * 0.26, width - pad * 2 - 6);
        doc.addImage(code.url, 'PNG', x + (width - barWidth) / 2, y + height - 11.2, barWidth, 7, undefined, 'FAST');
        doc.setFontSize(5.5);
        doc.text(item.code, x + width / 2, y + height - 1.6, { align: 'center' });
        index++;
      }
    }

    const url = String(doc.output('bloburl'));
    const opened = window.open(url, '_blank');
    if (!opened) {
      doc.save('etiquetas.pdf');
    }
  }
}
