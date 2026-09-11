import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-inventory',
  imports: [FormsModule],
  templateUrl: './inventory.html',
  styleUrl: './inventory.scss',
  standalone: true
})
export class InventoryComponent {
  constructor(protected store: StoreService) {}
}
