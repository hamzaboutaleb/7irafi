import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationService } from '../../services/notification.js';
import { AppNotification } from '../../types/notification.js';

/** The list of recent notifications, shown on the profile page. */
@Component({
  selector: 'app-notification-panel',
  imports: [DatePipe],
  templateUrl: './notification-panel.html',
})
export class NotificationPanel {
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  readonly items = this.notifications.list;
  readonly unread = this.notifications.unread;

  activate(notification: AppNotification): void {
    this.notifications.markRead(notification);

    if (notification.targetUrl) {
      void this.router.navigateByUrl(notification.targetUrl);
    }
  }

  markAllRead(): void {
    this.notifications.markAllRead();
  }
}
