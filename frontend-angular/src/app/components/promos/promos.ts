import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-promos',
  imports: [FormsModule],
  templateUrl: './promos.html',
  styleUrl: './promos.scss',
  standalone: true,
})
export class PromosComponent {
  constructor(protected store: StoreService) {}
}
