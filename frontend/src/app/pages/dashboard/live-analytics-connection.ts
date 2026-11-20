import { inject, Injectable, OnDestroy, signal } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { environment } from '../../../environments/environment.js';
import { AuthService } from '../../services/auth.js';

@Injectable()
export class LiveAnalyticsConnection implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly connectionStatusState = signal<
    'connecting' | 'connected' | 'disconnected' | 'error'
  >('disconnected');
  private readonly updateEvents = new Subject<'connected' | 'activity'>();
  private analyticsSocket: Client | null = null;

  readonly status = this.connectionStatusState.asReadonly();
  readonly updates$ = this.updateEvents.asObservable();

  ngOnDestroy(): void {
    this.disconnect();
    this.updateEvents.complete();
  }

  connect(token: string): void {
    const client = new Client({
      brokerURL: environment.analyticsSocketUrl || this.buildSameHostSocketUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: () => {
        if (this.auth.getToken() !== token) {
          void client.deactivate();
          return;
        }
        client.connectHeaders = { Authorization: `Bearer ${token}` };
      },
      onConnect: () => {
        if (!this.isCurrentLiveConnection(client, token)) return;

        client.subscribe('/user/queue/analytics', (frame) =>
          this.handleLiveUpdate(client, token, frame.body),
        );
        this.connectionStatusState.set('connected');
        this.updateEvents.next('connected');
      },
      onWebSocketClose: () => {
        if (this.analyticsSocket === client && this.connectionStatusState() !== 'error') {
          this.connectionStatusState.set('disconnected');
        }
      },
      onStompError: () => this.stopLiveConnectionWithError(client),
    });

    this.analyticsSocket = client;
    this.connectionStatusState.set('connecting');
    client.activate();
  }

  private isCurrentLiveConnection(client: Client, token: string): boolean {
    return this.analyticsSocket === client && this.auth.getToken() === token;
  }

  private handleLiveUpdate(client: Client, token: string, body: string): void {
    if (!this.isCurrentLiveConnection(client, token)) return;

    if (isAnalyticsUpdateMessage(body)) {
      this.updateEvents.next('activity');
      return;
    }

    this.stopLiveConnectionWithError(client);
  }

  private stopLiveConnectionWithError(client: Client): void {
    if (this.analyticsSocket === client) this.connectionStatusState.set('error');
    void client.deactivate();
  }

  disconnect(): void {
    const client = this.analyticsSocket;
    this.analyticsSocket = null;
    this.connectionStatusState.set('disconnected');
    void client?.deactivate();
  }

  private buildSameHostSocketUrl(): string {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}/ws/analytics`;
  }
}

function isAnalyticsUpdateMessage(body: string): boolean {
  try {
    const message = JSON.parse(body);
    return message?.resource === 'jobs' || message?.resource === 'applications';
  } catch {
    return false;
  }
}
