import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

/** Ventas de un periodo: totales, por dia, por categoria, productos, metodo y quien cobro. */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-report-range',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './report-range.html',
  styleUrls: ['../report-panel-content/report-panel-content.scss', './report-range.scss'],
})
export class ReportRangeComponent implements OnInit {
  constructor(protected store: StoreService) {}

  ngOnInit(): void {
    if (!this.store.rangeReport && !this.store.rangeLoading) {
      this.store.loadRangeReport();
    }
  }
}
