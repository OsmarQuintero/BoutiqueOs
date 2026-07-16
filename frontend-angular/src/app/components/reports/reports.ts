import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ReportHistoryComponent } from '../report-history/report-history';
import { ReportInventoryMovementsComponent } from '../report-inventory-movements/report-inventory-movements';
import { ReportRefundsComponent } from '../report-refunds/report-refunds';
import { ReportSalesComponent } from '../report-sales/report-sales';
import { ReportSummaryComponent } from '../report-summary/report-summary';
import { ReportTicketsComponent } from '../report-tickets/report-tickets';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-reports',
  imports: [
    FormsModule,
    ReportHistoryComponent,
    ReportSummaryComponent,
    ReportSalesComponent,
    ReportTicketsComponent,
    ReportRefundsComponent,
    ReportInventoryMovementsComponent
  ],
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
  standalone: true
})
export class ReportsComponent {
  constructor(protected store: StoreService) {}
}
