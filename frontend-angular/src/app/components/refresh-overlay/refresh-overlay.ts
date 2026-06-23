import { Component } from '@angular/core';
import { RefreshService } from '../../services/refresh.service';

@Component({
  selector: 'app-refresh-overlay',
  templateUrl: './refresh-overlay.html',
  styleUrl: './refresh-overlay.scss',
})
export class RefreshOverlayComponent {
  constructor(protected refresh: RefreshService) {}
}
