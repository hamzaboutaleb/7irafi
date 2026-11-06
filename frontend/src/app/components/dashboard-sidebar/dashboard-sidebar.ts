import { DatePipe } from '@angular/common';
import { Component, computed, EventEmitter, inject, Input, Output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { NotificationService } from '../../services/notification.js';
import { UserRatingSummary } from '../../services/rating.js';
import { RatingDisplayComponent } from '../rating-display/rating-display.js';

/** Activity stays live through the shared notification service, independent of job loading. */
@Component({
  selector: 'app-dashboard-sidebar',
  imports: [DatePipe, RouterLink, TranslatePipe, RatingDisplayComponent],
  templateUrl: './dashboard-sidebar.html',
  styleUrl: './dashboard-sidebar.css',
})
export class DashboardSidebar {
  @Input() summary: UserRatingSummary | null = null;
  @Input() summaryState: 'idle' | 'loading' | 'loaded' | 'error' = 'idle';
  @Output() retrySummary = new EventEmitter<void>();
  readonly notifications = inject(NotificationService);
  readonly recentActivity = computed(() => this.notifications.list().slice(0, 5));
}
