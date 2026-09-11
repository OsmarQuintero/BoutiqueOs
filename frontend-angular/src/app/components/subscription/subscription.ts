import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { StoreService } from '../../services/store.service';

/** Plan actual, cambio mensual/anual, portal de pagos de Stripe y cancelacion. */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-subscription',
  imports: [],
  templateUrl: './subscription.html',
  styleUrl: './subscription.scss',
})
export class SubscriptionComponent implements OnInit {
  constructor(protected store: StoreService) {}

  ngOnInit(): void {
    this.store.loadPlans();
    this.store.loadFeatures();
    this.store.loadSubscription();
  }
}
