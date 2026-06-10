import { Component } from '@angular/core';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-reports',
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
  standalone: true
})
export class ReportsComponent {
  constructor(protected store: StoreService) {}
}
