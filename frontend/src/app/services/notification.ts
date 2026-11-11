import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { environment } from '../../environments/environment.js';
import { Page } from '../types/api.js';
import { AppNotification } from '../types/notification.js';
import { AuthService } from './auth.js';

/**
 * The signed-in person's notifications.
 *
 * Kept in one root service rather than in the bell component, because the
 * socket must stay open while they are anywhere in the app — a notification is
 * precisely the thing you need when you are not looking at the right page.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private client: Client | null = null;
  private connectedToken: string | null = null;

  private readonly items = signal<AppNotification[]>([]);
  readonly list = this.items.asReadonly();
  readonly loading = signal(false);
  readonly loadError = signal(false);
  readonly unread = computed(() => this.items().filter((item) => !item.read).length);

  /** Safe to call repeatedly; reconnects only if the tab's account changed. */
  connect(): void {
    const token = this.auth.getToken();
    if (!token) {
      this.disconnect();
      return;
    }

    if (this.client && this.connectedToken === token) return;

    this.disconnect();
    this.connectedToken = token;
    this.load();

    this.client = new Client({
      brokerURL: environment.notificationSocketUrl || this.sameHostSocketUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: (client) => {
        client.connectHeaders = { Authorization: `Bearer ${this.auth.getToken() ?? ''}` };
      },
      onConnect: () => {
        this.client?.subscribe('/user/queue/notifications', (frame) => {
          const notification = JSON.parse(frame.body) as AppNotification;
          // Prepended, and deduplicated by id: a reload after a reconnect can
          // return something already shown live.
          this.items.update((current) => [
            notification,
            ...current.filter((item) => item.id !== notification.id),
          ]);
        });

        // Anything that arrived while the socket was down is only in the database.
        this.load();
      },
      onStompError: () => this.disconnect(),
    });

    this.client.activate();
  }

  disconnect(): void {
    const client = this.client;
    this.client = null;
    this.connectedToken = null;
    this.items.set([]);
    this.loading.set(false);
    this.loadError.set(false);
    void client?.deactivate();
  }

  load(): void {
    const token = this.connectedToken;
    this.loading.set(true);
    this.loadError.set(false);
    this.http.get<Page<AppNotification>>('notifications', { params: { page: 0, size: 20 } })
      .subscribe({
        next: (page) => {
          if (this.connectedToken !== token) return;
          this.items.set(page.content);
          this.loading.set(false);
        },
        error: () => {
          if (this.connectedToken !== token) return;
          // Keep existing activity visible, but distinguish failure from no activity.
          this.loading.set(false);
          this.loadError.set(true);
        },
      });
  }

  markRead(notification: AppNotification): void {
    if (notification.read) return;

    // Updated immediately: the person clicked it, and a failed call is corrected
    // by the next load.
    this.items.update((current) =>
      current.map((item) => (item.id === notification.id ? { ...item, read: true } : item)));

    this.http.patch<AppNotification>(`notifications/${notification.id}/read`, null)
      .subscribe({ error: () => this.load() });
  }

  markAllRead(): void {
    this.items.update((current) => current.map((item) => ({ ...item, read: true })));

    this.http.patch<{ unread: number }>('notifications/read', null)
      .subscribe({ error: () => this.load() });
  }

  private sameHostSocketUrl(): string {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}/notifications-ws`;
  }
}
