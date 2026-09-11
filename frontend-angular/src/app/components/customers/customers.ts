import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-customers',
  imports: [FormsModule],
  templateUrl: './customers.html',
  styleUrl: './customers.scss',
  standalone: true
})
export class CustomersComponent {
  constructor(protected store: StoreService) {}
}
