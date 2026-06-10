import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-pos',
  imports: [FormsModule],
  templateUrl: './pos.html',
  styleUrl: './pos.scss',
  standalone: true
})
export class PosComponent {
  constructor(protected store: StoreService) {}
}
