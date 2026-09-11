import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';
import { ProductImportComponent } from '../product-import/product-import';
import { ProductLabelsComponent } from '../product-labels/product-labels';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-products',
  imports: [FormsModule, ProductImportComponent, ProductLabelsComponent],
  templateUrl: './products.html',
  styleUrl: './products.scss',
  standalone: true
})
export class ProductsComponent {
  constructor(protected store: StoreService) {}
}
