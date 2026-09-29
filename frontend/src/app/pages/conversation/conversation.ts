import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, filter, map, of, switchMap } from 'rxjs';
import { Footer } from '../../components/footer/footer.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { AuthService } from '../../services/auth.js';
import { ChatService, chatError } from '../../services/chat.js';
import { I18nService } from '../../services/i18n.js';
import { JobService } from '../../services/job.js';
import { UserService } from '../../services/user.js';
import { ChatMessage, Conversation as ConversationModel } from '../../types/chat.js';

const PAGE_SIZE = 20;
const MAX_LENGTH = 4000;

@Component({
  selector: 'app-conversation',
  imports: [DatePipe, FormsModule, RouterLink, Navbar, Footer, TranslatePipe],
  templateUrl: './conversation.html',
})
export class Conversation implements OnInit {
  private readonly chat = inject(ChatService);
  private readonly i18n = inject(I18nService);
  private readonly jobs = inject(JobService);
  private readonly users = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  readonly me = this.auth.currentUserId();
  readonly maxLength = MAX_LENGTH;
  readonly connection = this.chat.state;

  readonly conversation = signal<ConversationModel | null>(null);
  readonly title = signal<string>('');
  readonly otherName = signal<string | null>(null);
  /** Oldest first, the order a person reads in. */
  readonly messages = signal<ChatMessage[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly hasOlder = signal(false);
  readonly loadingOlder = signal(false);
  draft = '';

  private conversationId = '';
  private oldestPage = 0;

  ngOnInit(): void {
    this.chat.connect();

    this.route.paramMap.pipe(
      map((params) => params.get('id') ?? ''),
      switchMap((id) => {
        this.conversationId = id;
        this.reset();
        return this.chat.get(id);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (conversation) => {
        this.conversation.set(conversation);
        this.loadLabels(conversation);
        this.loadLatest();
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(chatError(error, 'Unable to open this conversation.'));
        this.loading.set(false);
      },
    });

    // Live messages for every conversation arrive on the same subscription;
    // keep only this one's.
    this.chat.messages$.pipe(
      filter((message) => message.conversationId === this.conversationId),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((message) => this.merge([message]));

    // Whatever was sent while the socket was down never reached us.
    this.chat.reconnected$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadLatest());
  }

  isMine(message: ChatMessage): boolean {
    return message.senderId === this.me;
  }

  send(): void {
    const text = this.draft.trim();
    if (!text) return;

    // Not appended here: the server echoes it back with its real id and time,
    // and that echo is what gets shown.
    if (this.chat.send(this.conversationId, text)) {
      this.draft = '';
      this.notice.set(null);
    } else {
      this.notice.set(this.i18n.translate('chat.notConnected'));
    }
  }

  onEnter(event: Event): void {
    const keyboard = event as KeyboardEvent;
    if (keyboard.shiftKey) return;
    keyboard.preventDefault();
    this.send();
  }

  loadOlder(): void {
    if (this.loadingOlder() || !this.hasOlder()) return;
    this.loadingOlder.set(true);

    this.chat.history(this.conversationId, this.oldestPage + 1, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.oldestPage = page.page;
          this.hasOlder.set(page.page + 1 < page.totalPages);
          this.merge(page.content, false);
          this.loadingOlder.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.notice.set(chatError(error, 'Unable to load older messages.'));
          this.loadingOlder.set(false);
        },
      });
  }

  private reset(): void {
    this.conversation.set(null);
    this.messages.set([]);
    this.error.set(null);
    this.notice.set(null);
    this.loading.set(true);
    this.oldestPage = 0;
  }

  private loadLatest(): void {
    this.chat.history(this.conversationId, 0, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.hasOlder.set(page.totalPages > 1);
          this.merge(page.content);
          this.loading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.error.set(chatError(error, 'Unable to load messages.'));
          this.loading.set(false);
        },
      });
  }

  private loadLabels(conversation: ConversationModel): void {
    this.jobs.getJob(conversation.jobId).pipe(
      map((job) => job.title),
      catchError(() => of('Job no longer available')),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((title) => this.title.set(title));

    if (conversation.clientId === this.me) {
      this.users.getProfessional(conversation.workerId).pipe(
        map((response) => response.data.firstName),
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef),
      ).subscribe((name) => this.otherName.set(name));
    }
  }

  /**
   * One way in for history pages, reloads and live messages alike. Keyed by id,
   * so a message seen twice (live, then again in a reload) shows once.
   */
  private merge(incoming: ChatMessage[], scrollToBottom = true): void {
    const byId = new Map(this.messages().map((message) => [message.id, message]));
    for (const message of incoming) byId.set(message.id, message);

    // Compared as times, not strings: live echoes carry nanoseconds and stored
    // rows carry microseconds, so their text differs for the same instant.
    this.messages.set([...byId.values()].sort((a, b) =>
      Date.parse(a.sentAt) - Date.parse(b.sentAt) || a.id.localeCompare(b.id)));

    if (scrollToBottom) {
      setTimeout(() => {
        const element = this.scroller()?.nativeElement;
        if (element) element.scrollTop = element.scrollHeight;
      });
    }
  }
}
