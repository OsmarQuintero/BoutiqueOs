import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-products',
  imports: [FormsModule],
  templateUrl: './products.html',
  styleUrl: './products.scss',
  standalone: true
})
export class ProductsComponent {
  constructor(protected store: StoreService) {}
}
