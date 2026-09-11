import { ChangeDetectionStrategy, Component } from '@angular/core';
import { StoreService } from '../../services/store.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-inventory-movements',
  standalone: true,
  templateUrl: './inventory-movements.html',
  styleUrl: './inventory-movements.scss'
})
export class InventoryMovementsComponent {
  constructor(protected store: StoreService) {}
}
