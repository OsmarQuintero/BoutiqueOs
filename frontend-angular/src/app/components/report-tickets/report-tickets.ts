import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-report-tickets',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './report-tickets.html',
  styleUrl: '../report-panel-content/report-panel-content.scss'
})
export class ReportTicketsComponent {
  constructor(protected store: StoreService) {}
}
