import { Component } from '@angular/core';
import { ReportHistoryComponent } from '../report-history/report-history';
import { ReportInventoryMovementsComponent } from '../report-inventory-movements/report-inventory-movements';
import { ReportRefundsComponent } from '../report-refunds/report-refunds';
import { ReportSalesComponent } from '../report-sales/report-sales';
import { ReportSummaryComponent } from '../report-summary/report-summary';
import { ReportTicketsComponent } from '../report-tickets/report-tickets';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-report-panel-content',
  standalone: true,
  imports: [
    ReportHistoryComponent,
    ReportSummaryComponent,
    ReportSalesComponent,
    ReportTicketsComponent,
    ReportRefundsComponent,
    ReportInventoryMovementsComponent
  ],
  templateUrl: './report-panel-content.html',
  styleUrl: './report-panel-content.scss'
})
export class ReportPanelContentComponent {
  constructor(protected store: StoreService) {}
}
