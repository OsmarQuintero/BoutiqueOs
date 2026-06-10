import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from './services/store.service';
import { PosComponent } from './components/pos/pos';
import { ProductsComponent } from './components/products/products';
import { InventoryComponent } from './components/inventory/inventory';
import { CustomersComponent } from './components/customers/customers';
import { ReportsComponent } from './components/reports/reports';

@Component({
  selector: 'app-root',
  imports: [FormsModule, PosComponent, ProductsComponent, InventoryComponent, CustomersComponent, ReportsComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  constructor(protected store: StoreService) {}
}
