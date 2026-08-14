import { Component } from '@angular/core';
import { ProductsComponent } from '../products/products';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-catalog',
  imports: [ProductsComponent],
  templateUrl: './catalog.html',
  styleUrl: './catalog.scss',
  standalone: true,
})
export class CatalogComponent {
  constructor(protected store: StoreService) {}
}
