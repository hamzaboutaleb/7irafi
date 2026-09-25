import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationService } from '../../services/notification.js';
import { AppNotification } from '../../types/notification.js';

/** The bell in the navbar: unread count, and a panel of the recent ones. */
@Component({
  selector: 'app-notification-bell',
  imports: [],
  templateUrl: './notification-bell.html',
})
export class NotificationBell {
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly open = signal(false);
  readonly items = this.notifications.list;
  readonly unread = this.notifications.unread;

  @HostListener('document:click', ['$event'])
  closeOnOutsideClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) this.open.set(false);
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    this.open.set(false);
  }

  toggle(): void {
    this.open.update((value) => !value);
  }

  activate(notification: AppNotification): void {
    this.notifications.markRead(notification);
    this.open.set(false);

    if (notification.targetUrl) {
      void this.router.navigateByUrl(notification.targetUrl);
    }
  }

  markAllRead(): void {
    this.notifications.markAllRead();
  }
}
