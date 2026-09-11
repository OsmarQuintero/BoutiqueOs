import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-promos',
  imports: [FormsModule],
  templateUrl: './promos.html',
  styleUrl: './promos.scss',
  standalone: true,
})
export class PromosComponent implements OnInit {
  constructor(protected store: StoreService) {}

  ngOnInit(): void {
    // Se piden al abrir la vista porque el resto de la app solo carga las
    // recompensas activas, y aqui tambien hacen falta las pausadas.
    this.store.loadAllLoyaltyRewards();
  }
}
