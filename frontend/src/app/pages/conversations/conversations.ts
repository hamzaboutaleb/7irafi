import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, filter, forkJoin, map, of, switchMap } from 'rxjs';
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
  imports: [RouterLink, RouterLinkActive, RouterOutlet, Navbar, TranslatePipe, LucideSearch, LucideMessageSquare, LucideUserRound],
  templateUrl: './conversations.html',
  host: { class: 'block' },
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
    const normalize = (value: string) => value.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase();
    const query = normalize(this.query().trim());
    return this.rows().filter(row => normalize(`${this.nameFor(row)} ${row.jobTitle ?? this.i18n.translate('chat.jobUnavailable')}`).includes(query));
  });

  nameFor(row: ConversationRow): string {
    return row.otherName || this.i18n.translate(row.iAmClient ? 'chat.applicant' : 'chat.jobOwner');
  }

  ngOnInit(): void {
    this.selectedId.set(this.route.firstChild?.snapshot.paramMap.get('id') ?? null);
    this.router.events.pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.selectedId.set(this.route.firstChild?.snapshot.paramMap.get('id') ?? null));
    this.load();
  }

  load(): void {
    const me = this.auth.currentUserId();
    this.loading.set(true);
    this.error.set(null);

    this.chat.list().pipe(
      switchMap((conversations) => {
        if (conversations.length === 0) return of([]);

        // A conversation stores ids. The job title and the other participant's
        // public profile make each row recognisable. A failed lookup
        // degrades the label instead of hiding the conversation.
        return forkJoin(conversations.map((conversation) => {
          const iAmClient = conversation.clientId === me;
          return forkJoin({
            jobTitle: this.jobs.getJob(conversation.jobId).pipe(
              map((job) => job.title),
              catchError(() => of(null)),
            ),
            person: this.users.getPublicUserProfile(iAmClient ? conversation.workerId : conversation.clientId).pipe(
              map(response => response.data),
              catchError(() => of(null)),
            ),
          }).pipe(map(({ jobTitle, person }) => ({ conversation, jobTitle, iAmClient,
            otherName: person ? `${person.firstName} ${person.lastName}`.trim() || null : null,
            avatarUrl: person?.avatarUrl ?? null })));
        }));
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(chatError(error, this.i18n.translate('chat.listError')));
        this.loading.set(false);
      },
    });
  }
}
