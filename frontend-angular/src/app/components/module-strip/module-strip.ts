import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ViewId } from '../../services/store.service';

export interface ModuleStripItem {
  id: ViewId;
  label: string;
  iconPath: string;
}

@Component({
  selector: 'app-module-strip',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './module-strip.html',
  styleUrl: './module-strip.scss',
})
export class ModuleStripComponent {
  @Input({ required: true }) items: ModuleStripItem[] = [];
  @Input({ required: true }) activeView!: ViewId;
  @Input() statusText = 'Sin novedades';
  @Output() viewChange = new EventEmitter<ViewId>();

  isItemActive(viewId: ViewId): boolean {
    return this.activeView === viewId;
  }

  selectView(viewId: ViewId): void {
    this.viewChange.emit(viewId);
  }
}
