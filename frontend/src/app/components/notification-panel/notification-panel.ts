import { DatePipe } from '@angular/common';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { Component, computed, inject, input, output } from '@angular/core';
import { LucideBell, LucideX } from '@lucide/angular';
import { Router } from '@angular/router';
import { NotificationService } from '../../services/notification.js';
import { AppNotification } from '../../types/notification.js';

/** Shared activity list for the navbar and profile history. */
@Component({
  selector: 'app-notification-panel',
  imports: [DatePipe, TranslatePipe, LucideBell, LucideX],
  templateUrl: './notification-panel.html',
  styleUrl: './notification-panel.css',
})
export class NotificationPanel {
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  readonly compact = input(false);
  readonly headingId = input('notifications-heading');
  readonly activated = output<void>();
  readonly dismissRequested = output<void>();
  readonly items = computed(() => [...this.notifications.list()].sort(
    (a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)));
  readonly unread = this.notifications.unread;
  readonly loading = this.notifications.loading;
  readonly loadError = this.notifications.loadError;

  activate(notification: AppNotification): void {
    this.notifications.markRead(notification);
    this.activated.emit();

    if (notification.targetUrl) {
      void this.router.navigateByUrl(notification.targetUrl);
    }
  }

  markAllRead(): void {
    this.notifications.markAllRead();
  }

  retry(): void {
    this.notifications.load();
  }
}
