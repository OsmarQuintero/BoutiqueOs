import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-report-summary',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './report-summary.html',
  styleUrl: '../report-panel-content/report-panel-content.scss'
})
export class ReportSummaryComponent {
  constructor(protected store: StoreService) {}
}
