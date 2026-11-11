import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, HostListener, Injector, afterNextRender, inject, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, combineLatest, distinctUntilChanged, filter, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { LucideArrowLeft, LucideBriefcaseBusiness, LucideSend, LucideUserRound } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { AuthService } from '../../services/auth.js';
import { ChatService, chatError } from '../../services/chat.js';
import { I18nService } from '../../services/i18n.js';
import { JobService } from '../../services/job.js';
import { UserService } from '../../services/user.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { ChatMessage, Conversation as ConversationModel } from '../../types/chat.js';

const PAGE_SIZE = 20;
const MAX_LENGTH = 4000;

@Component({
  selector: 'app-conversation',
  imports: [DatePipe, FormsModule, RouterLink, TranslatePipe, LucideArrowLeft, LucideBriefcaseBusiness, LucideSend, LucideUserRound, JobDetailsModal],
  templateUrl: './conversation.html',
  host: { class: 'flex min-h-0 min-w-0 flex-1 flex-col' },
})
export class Conversation implements OnInit {
  private readonly chat = inject(ChatService);
  private readonly i18n = inject(I18nService);
  private readonly jobs = inject(JobService);
  private readonly users = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  private readonly draftInput = viewChild<ElementRef<HTMLTextAreaElement>>('draftInput');
  private readonly retryRequest = new Subject<void>();
  private readonly drafts = new Map<string, string>();

  readonly me = this.auth.currentUserId();
  readonly maxLength = MAX_LENGTH;
  readonly connection = this.chat.state;
  readonly conversation = signal<ConversationModel | null>(null);
  readonly title = signal<string | null>(null);
  readonly otherName = signal<string | null>(null);
  readonly avatarUrl = signal<string | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly hasOlder = signal(false);
  readonly loadingOlder = signal(false);
  readonly jobOpen = signal(false);
  draft = '';
  private conversationId = '';
  private oldestPage = 0;

  ngOnInit(): void {
    this.chat.connect();
    combineLatest([
      this.route.paramMap.pipe(map(params => params.get('id') ?? ''), distinctUntilChanged()),
      this.retryRequest.pipe(startWith(undefined)),
    ]).pipe(
      switchMap(([id]) => {
        if (this.conversationId) this.drafts.set(this.conversationId, this.draft);
        this.conversationId = id;
        this.reset();
        this.draft = this.drafts.get(id) ?? '';
        return this.chat.get(id).pipe(
          switchMap(conversation => forkJoin({
            history: this.chat.history(id, 0, PAGE_SIZE),
            title: this.jobs.getJob(conversation.jobId).pipe(map(job => job.title), catchError(() => of(null))),
            person: this.users.getPublicUserProfile(conversation.clientId === this.me ? conversation.workerId : conversation.clientId)
              .pipe(map(response => response.data), catchError(() => of(null))),
          }).pipe(map(data => ({ ...data, conversation })))),
          // Keep the route subscription alive so another thread or Retry can recover.
          catchError((error: HttpErrorResponse) => {
            this.error.set(chatError(error, this.i18n.translate('chat.openError')));
            this.loading.set(false);
            return of(null);
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(data => {
      if (!data) return;
      this.conversation.set(data.conversation);
      this.title.set(data.title);
      this.otherName.set(data.person ? `${data.person.firstName} ${data.person.lastName}`.trim() || null : null);
      this.avatarUrl.set(data.person?.avatarUrl ?? null);
      this.hasOlder.set(data.history.totalPages > 1);
      this.merge(data.history.content);
      this.loading.set(false);
      afterNextRender(() => this.resizeDraft(), { injector: this.injector });
    });
    this.chat.messages$.pipe(
      filter(message => message.conversationId === this.conversationId && !this.error()),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(message => this.merge([message]));
    this.chat.reconnected$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadLatest());
  }

  displayName(): string {
    return this.otherName() || this.i18n.translate(this.conversation()?.clientId === this.me ? 'chat.applicant' : 'chat.jobOwner');
  }

  retry(): void { this.retryRequest.next(); }
  isMine(message: ChatMessage): boolean { return message.senderId === this.me; }

  send(): void {
    const text = this.draft.trim();
    if (!text || text.length > MAX_LENGTH || this.loading() || this.error()) return;
    if (this.chat.send(this.conversationId, text)) {
      this.draft = '';
      this.drafts.delete(this.conversationId);
      this.notice.set(null);
      afterNextRender(() => {
        this.resizeDraft();
        this.draftInput()?.nativeElement.focus({ preventScroll: true });
      }, { injector: this.injector });
    } else this.notice.set(this.i18n.translate('chat.notConnected'));
  }

  onEnter(event: Event): void {
    const keyboard = event as KeyboardEvent;
    if (keyboard.shiftKey || keyboard.isComposing || window.matchMedia('(max-width: 767px), (pointer: coarse)').matches) return;
    keyboard.preventDefault();
    this.send();
  }

  @HostListener('window:resize')
  resizeDraft(): void {
    const element = this.draftInput()?.nativeElement;
    if (!element) return;
    element.style.height = '0px';
    element.style.height = `${Math.min(160, Math.max(40, element.scrollHeight))}px`;
    element.style.overflowY = element.scrollHeight > 160 ? 'auto' : 'hidden';
  }

  updateDraft(event: Event): void {
    this.draft = (event.target as HTMLTextAreaElement).value;
    this.resizeDraft();
  }

  loadOlder(): void {
    if (this.loadingOlder() || !this.hasOlder()) return;
    const id = this.conversationId;
    const element = this.scroller()?.nativeElement;
    const height = element?.scrollHeight ?? 0;
    const top = element?.scrollTop ?? 0;
    this.loadingOlder.set(true);
    this.chat.history(id, this.oldestPage + 1, PAGE_SIZE).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        if (id !== this.conversationId) return;
        this.oldestPage = page.page;
        this.hasOlder.set(page.page + 1 < page.totalPages);
        this.merge(page.content, false);
        this.loadingOlder.set(false);
        afterNextRender(() => {
          if (element && id === this.conversationId) element.scrollTop = top + element.scrollHeight - height;
        }, { injector: this.injector });
      },
      error: (error: HttpErrorResponse) => {
        if (id !== this.conversationId) return;
        this.notice.set(chatError(error, this.i18n.translate('chat.olderError')));
        this.loadingOlder.set(false);
      },
    });
  }

  private reset(): void {
    this.conversation.set(null);
    this.title.set(null);
    this.otherName.set(null);
    this.avatarUrl.set(null);
    this.messages.set([]);
    this.error.set(null);
    this.notice.set(null);
    this.loading.set(true);
    this.hasOlder.set(false);
    this.loadingOlder.set(false);
    this.jobOpen.set(false);
    this.oldestPage = 0;
  }

  private loadLatest(): void {
    if (!this.conversation() || this.loading()) return;
    const id = this.conversationId;
    this.chat.history(id, 0, PAGE_SIZE).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        if (id !== this.conversationId) return;
        // A reconnect must not reset pagination after older pages have been loaded.
        if (this.oldestPage === 0) this.hasOlder.set(page.totalPages > 1);
        this.merge(page.content);
      },
      error: (error: HttpErrorResponse) => {
        if (id === this.conversationId) this.notice.set(chatError(error, this.i18n.translate('chat.messagesError')));
      },
    });
  }

  private merge(incoming: ChatMessage[], scrollToBottom = true): void {
    const byId = new Map(this.messages().map(message => [message.id, message]));
    for (const message of incoming) byId.set(message.id, message);
    this.messages.set([...byId.values()].sort((a, b) => Date.parse(a.sentAt) - Date.parse(b.sentAt) || a.id.localeCompare(b.id)));
    if (scrollToBottom) {
      const id = this.conversationId;
      afterNextRender(() => {
        const element = this.scroller()?.nativeElement;
        if (element && id === this.conversationId) element.scrollTop = element.scrollHeight;
      }, { injector: this.injector });
    }
  }
}
