import { Component } from '@angular/core';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-subscription',
  imports: [],
  templateUrl: './subscription.html',
  styleUrl: './subscription.scss',
})
export class SubscriptionComponent {
  constructor(protected store: StoreService) {}

  get usagePercent(): number {
    const usage = this.store.subscription?.usage;
    if (!usage) return 0;
    if (usage.maxProducts < 0) return 0;
    return Math.min(100, Math.round((usage.productCount / usage.maxProducts) * 100));
  }

  get customerUsagePercent(): number {
    const usage = this.store.subscription?.usage;
    if (!usage) return 0;
    if (usage.maxCustomers < 0) return 0;
    return Math.min(100, Math.round((usage.customerCount / usage.maxCustomers) * 100));
  }

  get salesUsagePercent(): number {
    const usage = this.store.subscription?.usage;
    if (!usage) return 0;
    if (usage.maxSalesPerMonth < 0) return 0;
    return Math.min(100, Math.round((usage.salesThisMonth / usage.maxSalesPerMonth) * 100));
  }

  formatDate(dateStr: string | null): string {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleDateString('es-MX', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    } catch {
      return dateStr;
    }
  }
}
