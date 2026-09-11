import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StoreService } from '../../services/store.service';
import { SubscriptionComponent } from '../subscription/subscription';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-settings',
  imports: [FormsModule, SubscriptionComponent],
  templateUrl: './settings.html',
  styleUrl: './settings.scss'
})
export class SettingsComponent {
  constructor(protected store: StoreService) {}
}
