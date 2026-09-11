import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CatalogToolsService, LabelItem } from '../../services/catalog-tools.service';
import { Product, StoreService } from '../../services/store.service';

/** Etiquetas con codigo de barras para los productos marcados en el catalogo. */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-product-labels',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './product-labels.html',
  styleUrl: './product-labels.scss',
})
export class ProductLabelsComponent {
  format: 'roll' | 'sheet' = 'roll';
  copies: Record<number, number> = {};
  isBuilding = false;
  message = '';

  constructor(
    protected store: StoreService,
    private tools: CatalogToolsService,
  ) {}

  get selected(): Product[] {
    return this.store.products.filter((product) => this.store.labelSelection.includes(product.id));
  }

  /** Una etiqueta por pieza en existencia (minimo una). */
  copiesFor(product: Product): number {
    return this.copies[product.id] ?? Math.max(1, product.stock);
  }

  setCopies(product: Product, value: number | string): void {
    const copies = Math.max(0, Math.min(500, Math.round(Number(value) || 0)));
    this.copies = { ...this.copies, [product.id]: copies };
  }

  get totalLabels(): number {
    return this.selected.reduce((sum, product) => sum + this.copiesFor(product), 0);
  }

  async build(): Promise<void> {
    const items: LabelItem[] = this.selected
      .map((product) => ({
        name: product.name,
        variant: this.store.productVariantLabel(product).replace('—', ''),
        price: this.store.formatMoney(product.salePrice),
        code: this.store.labelCode(product),
        copies: this.copiesFor(product),
      }))
      .filter((item) => item.copies > 0);
    if (!items.length) {
      this.message = this.store.t('labels.none');
      return;
    }
    this.isBuilding = true;
    this.message = '';
    try {
      await this.tools.buildLabels(items, this.format, this.store.settings.storeName || 'Boutique OS');
      this.message = this.store.t('labels.ready', { n: this.totalLabels });
    } catch {
      this.message = this.store.t('labels.failed');
    } finally {
      this.isBuilding = false;
    }
  }

  close(): void {
    this.store.labelPanelOpen = false;
  }

  clear(): void {
    this.store.labelSelection = [];
    this.store.labelPanelOpen = false;
  }
}
