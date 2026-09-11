import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

/** Apartados: lista, detalle, abonos y cancelacion. Se crean desde el punto de venta. */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-layaways',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './layaways.html',
  styleUrl: './layaways.scss',
})
export class LayawaysComponent {
  constructor(protected store: StoreService) {}
}
