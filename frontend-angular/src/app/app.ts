import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CategoriesComponent } from './components/categories/categories';
import { CatalogComponent } from './components/catalog/catalog';
import { CustomersComponent } from './components/customers/customers';
import { InventoryComponent } from './components/inventory/inventory';
import { PosComponent } from './components/pos/pos';
import { RefreshOverlayComponent } from './components/refresh-overlay/refresh-overlay';
import { ReportsComponent } from './components/reports/reports';
import { SettingsComponent } from './components/settings/settings';
import { StoreService, ViewId } from './services/store.service';

interface NavItem {
  id: ViewId;
  label: string;
  iconPath: string;
}

@Component({
  selector: 'app-root',
  imports: [
    FormsModule,
    PosComponent,
    CatalogComponent,
    CategoriesComponent,
    InventoryComponent,
    CustomersComponent,
    ReportsComponent,
    SettingsComponent,
    RefreshOverlayComponent,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  readonly navItems: NavItem[] = [
    {
      id: 'pos',
      label: 'Punto de venta',
      iconPath:
        'M3 7.75 12 3l9 4.75v8.5L12 21l-9-4.75zm9 2.02-6.75-3.56v8.12L12 17.9l6.75-3.57V6.21z',
    },
    {
      id: 'catalog',
      label: 'Productos',
      iconPath: 'M4 7.5 12 4l8 3.5v9L12 20l-8-3.5zm8 1.64L6.5 6.73v7.04L12 16.18l5.5-2.41V6.73z',
    },
    {
      id: 'categories',
      label: 'Categorias',
      iconPath:
        'M4 6.5A2.5 2.5 0 0 1 6.5 4h3l1.25 1.5H17.5A2.5 2.5 0 0 1 20 8v9.5A2.5 2.5 0 0 1 17.5 20h-11A2.5 2.5 0 0 1 4 17.5zm2.5-.5a.5.5 0 0 0-.5.5V8h12V8a.5.5 0 0 0-.5-.5H9.81L8.56 6z',
    },
    {
      id: 'inventory',
      label: 'Inventario',
      iconPath:
        'M9 4.5h6a1.5 1.5 0 0 1 1.5 1.5V7H18a2 2 0 0 1 2 2v8.5A2.5 2.5 0 0 1 17.5 20h-11A2.5 2.5 0 0 1 4 17.5V9a2 2 0 0 1 2-2h1.5V6A1.5 1.5 0 0 1 9 4.5m0 2V7h6V6.5zM7.5 11h9v1.5h-9zm0 3.5h6v1.5h-6z',
    },
    {
      id: 'customers',
      label: 'Clientes',
      iconPath:
        'M8.5 11a3 3 0 1 1 0-6 3 3 0 0 1 0 6m7 1a2.5 2.5 0 1 0-2.5-2.5A2.5 2.5 0 0 0 15.5 12m-7 1c-2.76 0-5 1.57-5 3.5V18h10v-1.5c0-1.93-2.24-3.5-5-3.5m7 1c-.91 0-1.77.18-2.5.5 1.19.73 2 1.81 2 3V18H20v-.8c0-1.78-2.02-3.2-4.5-3.2',
    },
    {
      id: 'reports',
      label: 'Corte diario',
      iconPath: 'M5 19.5V10h2v9.5zm6 0V4.5h2v15zm6 0V13h2v6.5z',
    },
    {
      id: 'settings',
      label: 'Configuracion',
      iconPath:
        'M12 8.5a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7m7.2 3.5c0-.45-.05-.89-.14-1.31l1.62-1.26-1.6-2.77-1.91.77a7.7 7.7 0 0 0-2.27-1.31L14.6 4h-3.2l-.3 2.12a7.7 7.7 0 0 0-2.27 1.31l-1.91-.77-1.6 2.77 1.62 1.26A6.5 6.5 0 0 0 6.8 12c0 .45.05.89.14 1.31l-1.62 1.26 1.6 2.77 1.91-.77a7.7 7.7 0 0 0 2.27 1.31l.3 2.12h3.2l.3-2.12a7.7 7.7 0 0 0 2.27-1.31l1.91.77 1.6-2.77-1.62-1.26c.09-.42.14-.86.14-1.31',
    },
  ];
  constructor(protected store: StoreService) {}

  isItemActive(viewId: ViewId): boolean {
    return this.store.activeView === viewId;
  }

  openView(view: ViewId): void {
    this.store.setView(view);
  }
}
