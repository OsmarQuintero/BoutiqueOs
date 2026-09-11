import { ChangeDetectionStrategy, Component } from '@angular/core';
import { StoreService } from '../../services/store.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-report-inventory-movements',
  standalone: true,
  templateUrl: './report-inventory-movements.html',
  styleUrl: './report-inventory-movements.scss'
})
export class ReportInventoryMovementsComponent {
  constructor(protected store: StoreService) {}
}
