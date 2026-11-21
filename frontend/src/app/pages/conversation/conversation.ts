import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  Injector,
  afterNextRender,
  inject,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  filter,
  forkJoin,
  map,
  of,
  startWith,
  switchMap,
  timer,
} from 'rxjs';
import {
  LucideArrowLeft,
  LucideBriefcaseBusiness,
  LucideSend,
  LucideUserRound,
} from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { AuthService } from '../../services/auth.js';
import { ChatService, chatError } from '../../services/chat.js';
import { I18nService } from '../../services/i18n.js';
import { JobService } from '../../services/job.js';
import { UserService } from '../../services/user.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { ChatMessage, Conversation as ConversationModel } from '../../types/chat.js';
import { Page } from '../../types/api.js';
import { PublicUserProfile } from '../../types/user.js';

interface ConversationData {
  conversation: ConversationModel;
  history: Page<ChatMessage>;
  title: string | null;
  person: PublicUserProfile | null;
  otherUserId: string;
}

const PRESENCE_REFRESH_MS = 45_000;
const PAGE_SIZE = 20;
const MAX_LENGTH = 4000;

@Component({
  selector: 'app-conversation',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    TranslatePipe,
    LucideArrowLeft,
    LucideBriefcaseBusiness,
    LucideSend,
    LucideUserRound,
    JobDetailsModal,
  ],
  templateUrl: './conversation.html',
  styleUrl: './conversation.css',
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
  readonly otherOnline = signal<boolean | null>(null);
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
    this.listenForConversationChanges();
    this.listenForMessages();
    this.listenForReconnects();
  }

  private listenForConversationChanges(): void {
    const conversationId$ = this.route.paramMap.pipe(
      map((params) => params.get('id') ?? ''),
      distinctUntilChanged(),
    );
    const retry$ = this.retryRequest.pipe(startWith(undefined));

    combineLatest([conversationId$, retry$])
      .pipe(
        switchMap(([conversationId]) => {
          this.prepareConversation(conversationId);

          return this.loadConversation(conversationId).pipe(
            switchMap((data) => {
              if (!data) {
                return EMPTY;
              }

              this.showConversation(data);
              return timer(PRESENCE_REFRESH_MS, PRESENCE_REFRESH_MS).pipe(
                switchMap(() =>
                  this.users.getPublicUserProfile(data.otherUserId).pipe(
                    map((response) => response.data?.online ?? null),
                    catchError(() => of(null)),
                  ),
                ),
              );
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((online) => this.otherOnline.set(online));
  }

  private listenForMessages(): void {
    this.chat.messages$
      .pipe(
        filter((message) => {
          const belongsToCurrentConversation = message.conversationId === this.conversationId;

          return belongsToCurrentConversation && !this.error();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((message) => this.merge([message]));
  }

  private listenForReconnects(): void {
    this.chat.reconnected$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadLatest());
  }

  private prepareConversation(conversationId: string): void {
    if (this.conversationId) {
      this.drafts.set(this.conversationId, this.draft);
    }

    this.conversationId = conversationId;
    this.reset();
    this.otherOnline.set(null);
    this.draft = this.drafts.get(conversationId) ?? '';
  }

  private loadConversation(conversationId: string): Observable<ConversationData | null> {
    return this.chat.get(conversationId).pipe(
      switchMap((conversation) => {
        const isJobOwner = conversation.clientId === this.me;
        let otherUserId = conversation.clientId;

        if (isJobOwner) {
          otherUserId = conversation.workerId;
        }

        const history$ = this.chat.history(conversationId, 0, PAGE_SIZE);

        const title$ = this.jobs.getJob(conversation.jobId).pipe(
          map((job) => job.title),
          catchError(() => of(null)),
        );

        const person$ = this.users.getPublicUserProfile(otherUserId).pipe(
          map((response) => response.data),
          catchError(() => of(null)),
        );

        return forkJoin({
          conversation: of(conversation),
          history: history$,
          title: title$,
          person: person$,
          otherUserId: of(otherUserId),
        });
      }),
      // Handle failure here so route changes and Retry keep working.
      catchError((error: HttpErrorResponse) => {
        const fallback = this.i18n.translate('chat.openError');

        this.error.set(chatError(error, fallback));
        this.loading.set(false);

        return of(null);
      }),
    );
  }

  private showConversation(data: ConversationData): void {
    let otherName: string | null = null;

    if (data.person) {
      const fullName = `${data.person.firstName} ${data.person.lastName}`.trim();
      otherName = fullName || null;
    }

    this.conversation.set(data.conversation);
    this.title.set(data.title);
    this.otherName.set(otherName);
    this.otherOnline.set(data.person?.online ?? null);
    this.avatarUrl.set(data.person?.avatarUrl ?? null);
    this.hasOlder.set(data.history.totalPages > 1);
    this.merge(data.history.content);
    this.loading.set(false);

    afterNextRender(() => this.resizeDraft(), { injector: this.injector });
  }

  displayName(): string {
    const name = this.otherName();

    if (name) {
      return name;
    }

    const isJobOwner = this.conversation()?.clientId === this.me;

    if (isJobOwner) {
      return this.i18n.translate('chat.applicant');
    }

    return this.i18n.translate('chat.jobOwner');
  }

  retry(): void {
    this.retryRequest.next();
  }

  isMine(message: ChatMessage): boolean {
    return message.senderId === this.me;
  }

  send(): void {
    const text = this.draft.trim();

    if (!text || text.length > MAX_LENGTH) {
      return;
    }

    if (this.loading() || this.error()) {
      return;
    }

    const published = this.chat.send(this.conversationId, text);

    if (!published) {
      this.notice.set(this.i18n.translate('chat.notConnected'));
      return;
    }

    this.draft = '';
    this.drafts.delete(this.conversationId);
    this.notice.set(null);

    afterNextRender(
      () => {
        this.resizeDraft();
        this.draftInput()?.nativeElement.focus({ preventScroll: true });
      },
      { injector: this.injector },
    );
  }

  onEnter(event: Event): void {
    const keyboard = event as KeyboardEvent;
    const usesMobileInput = window.matchMedia('(max-width: 767px), (pointer: coarse)').matches;

    if (keyboard.shiftKey || usesMobileInput) {
      return;
    }

    keyboard.preventDefault();
    this.send();
  }

  @HostListener('window:resize')
  resizeDraft(): void {
    const textarea = this.draftInput()?.nativeElement;

    if (!textarea) {
      return;
    }

    const minimumHeight = 40;
    const maximumHeight = 160;

    textarea.style.height = '0px';

    const contentHeight = textarea.scrollHeight;
    const desiredHeight = Math.max(minimumHeight, contentHeight);
    const height = Math.min(maximumHeight, desiredHeight);

    textarea.style.height = `${height}px`;

    const needsScrolling = textarea.scrollHeight > maximumHeight;
    textarea.style.overflowY = needsScrolling ? 'auto' : 'hidden';
  }

  updateDraft(event: Event): void {
    this.draft = (event.target as HTMLTextAreaElement).value;
    this.resizeDraft();
  }

  loadOlder(): void {
    if (this.loadingOlder() || !this.hasOlder()) {
      return;
    }

    const conversationId = this.conversationId;
    const scroller = this.scroller()?.nativeElement;
    const previousHeight = scroller?.scrollHeight ?? 0;
    const previousScrollTop = scroller?.scrollTop ?? 0;
    const nextPage = this.oldestPage + 1;

    this.loadingOlder.set(true);

    this.chat
      .history(conversationId, nextPage, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          if (conversationId !== this.conversationId) {
            return;
          }

          this.oldestPage = page.page;
          this.hasOlder.set(page.page + 1 < page.totalPages);
          this.merge(page.content, false);
          this.loadingOlder.set(false);
          afterNextRender(
            () => {
              if (!scroller || conversationId !== this.conversationId) {
                return;
              }

              const addedHeight = scroller.scrollHeight - previousHeight;
              scroller.scrollTop = previousScrollTop + addedHeight;
            },
            { injector: this.injector },
          );
        },
        error: (error: HttpErrorResponse) => {
          if (conversationId !== this.conversationId) {
            return;
          }

          const fallback = this.i18n.translate('chat.olderError');

          this.notice.set(chatError(error, fallback));
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
    if (!this.conversation() || this.loading()) {
      return;
    }

    const conversationId = this.conversationId;

    this.chat
      .history(conversationId, 0, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          if (conversationId !== this.conversationId) {
            return;
          }

          // Preserve pagination when older messages have already been loaded.
          if (this.oldestPage === 0) {
            this.hasOlder.set(page.totalPages > 1);
          }

          this.merge(page.content);
        },
        error: (error: HttpErrorResponse) => {
          if (conversationId !== this.conversationId) {
            return;
          }

          const fallback = this.i18n.translate('chat.messagesError');

          this.notice.set(chatError(error, fallback));
        },
      });
  }

  private merge(incoming: ChatMessage[], scrollToBottom = true): void {
    const messagesById = new Map<string, ChatMessage>();

    for (const message of this.messages()) {
      messagesById.set(message.id, message);
    }

    for (const message of incoming) {
      messagesById.set(message.id, message);
    }

    const sortedMessages = [...messagesById.values()].sort((first, second) => {
      const timeDifference = Date.parse(first.sentAt) - Date.parse(second.sentAt);

      if (timeDifference) {
        return timeDifference;
      }

      return first.id.localeCompare(second.id);
    });

    this.messages.set(sortedMessages);

    if (scrollToBottom) {
      this.scrollToLatestMessage();
    }
  }

  private scrollToLatestMessage(): void {
    const conversationId = this.conversationId;

    afterNextRender(
      () => {
        if (conversationId !== this.conversationId) {
          return;
        }

        const scroller = this.scroller()?.nativeElement;

        if (scroller) {
          scroller.scrollTop = scroller.scrollHeight;
        }
      },
      { injector: this.injector },
    );
  }
}
