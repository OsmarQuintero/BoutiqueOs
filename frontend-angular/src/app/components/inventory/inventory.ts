import { Component } from '@angular/core';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-inventory',
  templateUrl: './inventory.html',
  styleUrl: './inventory.scss',
  standalone: true
})
export class InventoryComponent {
  constructor(protected store: StoreService) {}
}
