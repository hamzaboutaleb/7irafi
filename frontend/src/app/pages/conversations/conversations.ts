import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { Footer } from '../../components/footer/footer.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { AuthService } from '../../services/auth.js';
import { ChatService, chatError } from '../../services/chat.js';
import { JobService } from '../../services/job.js';
import { UserService } from '../../services/user.js';
import { Conversation } from '../../types/chat.js';

/** One row of the list, with the labels a person actually recognises. */
interface ConversationRow {
  conversation: Conversation;
  jobTitle: string;
  iAmClient: boolean;
  /** The worker's first name, when the viewer is the client. */
  otherName: string | null;
}

@Component({
  selector: 'app-conversations',
  imports: [DatePipe, RouterLink, Navbar, Footer],
  templateUrl: './conversations.html',
})
export class Conversations implements OnInit {
  private readonly chat = inject(ChatService);
  private readonly jobs = inject(JobService);
  private readonly users = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly rows = signal<ConversationRow[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const me = this.auth.currentUserId();
    this.loading.set(true);
    this.error.set(null);

    this.chat.list().pipe(
      switchMap((conversations) => {
        if (conversations.length === 0) return of([]);

        // A conversation only stores ids. The job title and, for the client,
        // the worker's name make each row recognisable. A failed lookup
        // degrades the label instead of hiding the conversation.
        return forkJoin(conversations.map((conversation) => {
          const iAmClient = conversation.clientId === me;
          return forkJoin({
            jobTitle: this.jobs.getJob(conversation.jobId).pipe(
              map((job) => job.title),
              catchError(() => of('Job no longer available')),
            ),
            otherName: iAmClient
              ? this.users.getProfessional(conversation.workerId).pipe(
                  map((response) => response.data.firstName),
                  catchError(() => of(null)),
                )
              : of(null),
          }).pipe(map(({ jobTitle, otherName }) => ({ conversation, jobTitle, iAmClient, otherName })));
        }));
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(chatError(error, 'Unable to load your conversations. Please try again.'));
        this.loading.set(false);
      },
    });
  }
}
