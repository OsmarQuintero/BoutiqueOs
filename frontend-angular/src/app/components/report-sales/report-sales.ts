import { Component } from '@angular/core';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-report-sales',
  standalone: true,
  templateUrl: './report-sales.html',
  styleUrl: '../report-panel-content/report-panel-content.scss'
})
export class ReportSalesComponent {
  constructor(protected store: StoreService) {}
}
