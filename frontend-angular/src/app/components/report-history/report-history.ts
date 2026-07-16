import { Component } from '@angular/core';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-report-history',
  standalone: true,
  templateUrl: './report-history.html',
  styleUrl: '../report-panel-content/report-panel-content.scss',
})
export class ReportHistoryComponent {
  constructor(protected store: StoreService) {}
}
