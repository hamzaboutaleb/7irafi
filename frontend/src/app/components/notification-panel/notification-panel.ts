import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output } from '@angular/core';
import { Router } from '@angular/router';
import { LucideBell, LucideX } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
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

  readonly unread = this.notifications.unread;
  readonly loading = this.notifications.loading;
  readonly loadError = this.notifications.loadError;

  readonly items = computed(() => {
    const notifications = [...this.notifications.list()];

    return notifications.sort(compareNewestFirst);
  });

  activate(notification: AppNotification): void {
    this.notifications.markRead(notification);
    this.activated.emit();

    const targetUrl = notification.targetUrl;

    if (!targetUrl) {
      return;
    }

    void this.router.navigateByUrl(targetUrl);
  }

  markAllRead(): void {
    this.notifications.markAllRead();
  }

  retry(): void {
    this.notifications.load();
  }
}

function compareNewestFirst(first: AppNotification, second: AppNotification): number {
  const firstCreatedAt = Date.parse(first.createdAt);
  const secondCreatedAt = Date.parse(second.createdAt);

  return secondCreatedAt - firstCreatedAt;
}
