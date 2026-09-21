import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { environment } from '../../environments/environment.js';
import { Page } from '../types/api.js';
import { ChatConnectionState, ChatMessage, Conversation } from '../types/chat.js';
import { AuthService } from './auth.js';

/**
 * Chat has two halves that travel differently:
 *  - REST (open a conversation, list them, read history) goes through the
 *    gateway like every other API call, so these URLs are relative.
 *  - Live messages use one STOMP websocket straight to chat-service.
 */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private client: Client | null = null;
  private connectedToken: string | null = null;

  private readonly incoming = new Subject<ChatMessage>();
  /** Every message pushed by the server, for whichever conversation. */
  readonly messages$ = this.incoming.asObservable();

  private readonly reconnected = new Subject<void>();
  /**
   * Fires after a dropped connection comes back. Anything sent while we were
   * away never reached this socket, so pages reload their history on it.
   */
  readonly reconnected$ = this.reconnected.asObservable();

  private readonly stateSignal = signal<ChatConnectionState>('disconnected');
  readonly state = this.stateSignal.asReadonly();

  open(applicationId: string) {
    return this.http.post<Conversation>('conversations', { applicationId });
  }

  list() {
    return this.http.get<Conversation[]>('conversations');
  }

  get(conversationId: string) {
    return this.http.get<Conversation>(`conversations/${conversationId}`);
  }

  history(conversationId: string, page = 0, size = 20) {
    return this.http.get<Page<ChatMessage>>(`conversations/${conversationId}/messages`, {
      params: { page, size },
    });
  }

  /**
   * Safe to call from every chat page. Reuses the live connection unless the
   * signed-in account changed, in which case the old identity's socket is
   * closed first so it can never receive the new account's messages.
   */
  connect(): void {
    const token = this.auth.getToken();
    if (!token) {
      this.disconnect();
      return;
    }

    if (this.client && this.connectedToken === token) return;
    this.disconnect();

    let hasConnectedBefore = false;
    this.connectedToken = token;
    this.stateSignal.set('connecting');

    this.client = new Client({
      brokerURL: environment.chatSocketUrl || this.sameHostSocketUrl(),
      // Retried automatically after a drop: the subject asks for graceful
      // handling of disconnection, not a dead chat until the page is reloaded.
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      // Re-read before every attempt, so a reconnect never sends a stale token.
      beforeConnect: (client) => {
        client.connectHeaders = { Authorization: `Bearer ${this.auth.getToken() ?? ''}` };
      },
      onConnect: () => {
        this.stateSignal.set('connected');
        this.client?.subscribe('/user/queue/messages', (frame) => {
          this.incoming.next(JSON.parse(frame.body) as ChatMessage);
        });

        if (hasConnectedBefore) this.reconnected.next();
        hasConnectedBefore = true;
      },
      onWebSocketClose: () => {
        if (this.client) this.stateSignal.set('connecting');
      },
      onStompError: () => {
        // The server refuses CONNECT when the token is missing or invalid.
        // Retrying with the same token would loop forever, so stop here.
        this.disconnect();
      },
    });

    this.client.activate();
  }

  disconnect(): void {
    const client = this.client;
    this.client = null;
    this.connectedToken = null;
    this.stateSignal.set('disconnected');
    void client?.deactivate();
  }

  /** Returns false when there is no live connection to send on. */
  send(conversationId: string, text: string): boolean {
    if (!this.client?.connected) return false;

    this.client.publish({
      destination: '/app/chat',
      body: JSON.stringify({ conversationId, text }),
    });
    return true;
  }

  private sameHostSocketUrl(): string {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}/ws`;
  }
}

export function chatError(error: HttpErrorResponse, fallback: string): string {
  if (error.status === 0 || error.status === 503) {
    return 'Unable to reach the chat service. Please try again.';
  }

  const messages: Record<string, string> = {
    CONVERSATION_ACCESS_DENIED: 'You are not part of this conversation.',
    CONVERSATION_NOT_FOUND: 'This conversation no longer exists.',
    APPLICATION_NOT_FOUND: 'This application is no longer available.',
    APPLICATION_SERVICE_UNAVAILABLE: 'Unable to reach the application service. Please try again.',
  };
  return messages[error.error?.error?.code] ?? fallback;
}
