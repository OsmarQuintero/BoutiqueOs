import { Component } from '@angular/core';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-report-inventory-movements',
  standalone: true,
  templateUrl: './report-inventory-movements.html',
  styleUrl: './report-inventory-movements.scss'
})
export class ReportInventoryMovementsComponent {
  constructor(protected store: StoreService) {}
}
