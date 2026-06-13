import { Component } from '@angular/core';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-report-refunds',
  standalone: true,
  templateUrl: './report-refunds.html',
  styleUrl: '../report-panel-content/report-panel-content.scss'
})
export class ReportRefundsComponent {
  constructor(protected store: StoreService) {}
}
