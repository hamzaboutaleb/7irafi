import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { Observable, catchError, filter, forkJoin, map, of, switchMap } from 'rxjs';
import { LucideSearch, LucideMessageSquare, LucideUserRound } from '@lucide/angular';
import { I18nService } from '../../services/i18n.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { AuthService } from '../../services/auth.js';
import { ChatService, chatError } from '../../services/chat.js';
import { JobService } from '../../services/job.js';
import { UserService } from '../../services/user.js';
import { Conversation } from '../../types/chat.js';

/** One row of the list, with the labels a person actually recognises. */
interface ConversationRow {
  conversation: Conversation;
  jobTitle: string | null;
  iAmClient: boolean;
  /** The other participant's full name, for both clients and professionals. */
  otherName: string | null;
  avatarUrl: string | null;
}

@Component({
  selector: 'app-conversations',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    Navbar,
    TranslatePipe,
    LucideSearch,
    LucideMessageSquare,
    LucideUserRound,
  ],
  templateUrl: './conversations.html',
  styleUrl: './conversations.css',
})
export class Conversations implements OnInit {
  private readonly chat = inject(ChatService);
  private readonly jobs = inject(JobService);
  private readonly users = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  readonly rows = signal<ConversationRow[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly query = signal('');
  readonly selectedId = signal<string | null>(null);
  readonly filteredRows = computed(() => {
    const query = this.normalizeSearchText(this.query().trim());

    return this.rows().filter((row) => {
      const name = this.nameFor(row);
      const jobTitle = row.jobTitle ?? this.i18n.translate('chat.jobUnavailable');
      const searchableText = this.normalizeSearchText(`${name} ${jobTitle}`);

      return searchableText.includes(query);
    });
  });

  nameFor(row: ConversationRow): string {
    if (row.otherName) {
      return row.otherName;
    }

    if (row.iAmClient) {
      return this.i18n.translate('chat.applicant');
    }

    return this.i18n.translate('chat.jobOwner');
  }

  ngOnInit(): void {
    this.updateSelectedConversation();

    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.updateSelectedConversation());

    this.load();
  }

  load(): void {
    const currentUserId = this.auth.currentUserId();
    this.loading.set(true);
    this.error.set(null);

    this.chat
      .list()
      .pipe(
        switchMap((conversations) => {
          if (conversations.length === 0) {
            return of([]);
          }

          const rowRequests = conversations.map((conversation) =>
            this.loadConversationRow(conversation, currentUserId),
          );

          return forkJoin(rowRequests);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (rows) => {
          this.rows.set(rows);
          this.loading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          const fallback = this.i18n.translate('chat.listError');

          this.error.set(chatError(error, fallback));
          this.loading.set(false);
        },
      });
  }

  private loadConversationRow(
    conversation: Conversation,
    currentUserId: string | null,
  ): Observable<ConversationRow> {
    const iAmClient = conversation.clientId === currentUserId;
    let otherUserId = conversation.clientId;

    if (iAmClient) {
      otherUserId = conversation.workerId;
    }

    // Keep the conversation visible even if its job or profile lookup fails.
    const jobTitle$ = this.jobs.getJob(conversation.jobId).pipe(
      map((job) => job.title),
      catchError(() => of(null)),
    );

    const person$ = this.users.getPublicUserProfile(otherUserId).pipe(
      map((response) => response.data),
      catchError(() => of(null)),
    );

    return forkJoin({ jobTitle: jobTitle$, person: person$ }).pipe(
      map(({ jobTitle, person }) => {
        let otherName: string | null = null;

        if (person) {
          const fullName = `${person.firstName} ${person.lastName}`.trim();
          otherName = fullName || null;
        }

        return {
          conversation,
          jobTitle,
          iAmClient,
          otherName,
          avatarUrl: person?.avatarUrl ?? null,
        };
      }),
    );
  }

  private updateSelectedConversation(): void {
    const conversationId = this.route.firstChild?.snapshot.paramMap.get('id') ?? null;

    this.selectedId.set(conversationId);
  }

  private normalizeSearchText(value: string): string {
    return value.toLocaleLowerCase();
  }
}
