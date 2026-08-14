import { Component } from '@angular/core';
import { StoreService } from '../../services/store.service';

@Component({
  selector: 'app-language-toggle',
  templateUrl: './language-toggle.html',
  styleUrl: './language-toggle.scss',
  standalone: true,
})
export class LanguageToggleComponent {
  constructor(protected store: StoreService) {}

  get active(): string {
    return this.store.lang;
  }

  switchTo(lang: 'es' | 'en'): void {
    if (lang !== this.store.lang) {
      this.store.toggleLang();
    }
  }
}
