import { ChangeDetectionStrategy, Component } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { CatalogToolsService, ImportPreviewRow } from '../../services/catalog-tools.service';
import { StoreService } from '../../services/store.service';

interface ImportResult {
  created: number;
  updated: number;
  skipped: number;
  errors: Array<{ line: number; message: string }>;
}

/** Importar productos desde Excel (.xlsx) o CSV, con vista previa antes de guardar. */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-product-import',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './product-import.html',
  styleUrl: './product-import.scss',
})
export class ProductImportComponent {
  fileName = '';
  rows: ImportPreviewRow[] = [];
  missing: string[] = [];
  readError = '';
  updateExisting = true;
  isImporting = false;
  result: ImportResult | null = null;
  serverError = '';

  constructor(
    protected store: StoreService,
    private tools: CatalogToolsService,
    private http: HttpClient,
  ) {}

  get validRows(): ImportPreviewRow[] {
    return this.rows.filter((row) => !row.problem);
  }

  get invalidCount(): number {
    return this.rows.length - this.validRows.length;
  }

  get preview(): ImportPreviewRow[] {
    return this.rows.slice(0, 100);
  }

  async pick(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.reset();
    this.fileName = file.name;
    try {
      const parsed = this.tools.toImportRows(await this.tools.readSpreadsheet(file));
      this.rows = parsed.rows;
      this.missing = parsed.missing;
      if (!this.rows.length && !this.missing.length) {
        this.readError = this.store.t('import.empty');
      }
    } catch (error) {
      this.readError =
        error instanceof Error && error.message === 'xls'
          ? this.store.t('import.oldXls')
          : this.store.t('import.unreadable');
    }
  }

  downloadTemplate(): void {
    const blob = new Blob([this.tools.templateCsv()], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'plantilla-productos.csv';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  runImport(): void {
    const rows = this.validRows;
    if (!rows.length || this.isImporting) return;
    this.isImporting = true;
    this.serverError = '';
    this.http
      .post<ImportResult>(this.store.apiUrl('/products/import'), {
        rows: rows.map((row) => ({
          line: row.line,
          name: row.name,
          category: row.category,
          size: row.size,
          color: row.color,
          sku: row.sku,
          costPrice: row.costPrice,
          salePrice: row.salePrice,
          stock: row.stock,
          minStock: row.minStock,
          styleCode: row.styleCode,
        })),
        updateExisting: this.updateExisting,
      })
      .pipe(finalize(() => (this.isImporting = false)))
      .subscribe({
        next: (result) => {
          this.result = result;
          this.rows = [];
          this.store.loadProducts();
          this.store.loadProductCategories();
        },
        error: (error: HttpErrorResponse) => {
          this.serverError = error.error?.message || this.store.t('import.failed');
        },
      });
  }

  close(): void {
    this.store.importPanelOpen = false;
    this.reset();
  }

  private reset(): void {
    this.fileName = '';
    this.rows = [];
    this.missing = [];
    this.readError = '';
    this.result = null;
    this.serverError = '';
  }
}
