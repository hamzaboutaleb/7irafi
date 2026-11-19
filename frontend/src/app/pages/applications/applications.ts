import { TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import {
  LucideDynamicIcon,
  LucideSearch,
  LucideBriefcaseBusiness,
  LucideMapPin,
  LucideMessageSquare,
  LucideStar,
  LucideChevronLeft,
  LucideChevronRight,
} from '@lucide/angular';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { ApplicationService } from '../../services/application.js';
import { AuthService } from '../../services/auth.js';
import { RatingService } from '../../services/rating.js';
import { JobService } from '../../services/job.js';
import { ChatService } from '../../services/chat.js';
import { I18nService } from '../../services/i18n.js';
import { JobApplication } from '../../types/application.js';
import { JobDetail } from '../../types/job.js';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

interface ApplicationRow extends JobApplication {
  job: JobDetail | null;
  jobMissing: boolean;
}

@Component({
  selector: 'app-applications',
  imports: [
    TitleCasePipe,
    RouterLink,
    Navbar,
    Footer,
    JobDetailsModal,
    TranslatePipe,
    LucideDynamicIcon,
  ],
  templateUrl: './applications.html',
  styleUrl: './applications.css',
})
export class Applications implements OnInit {
  private readonly service = inject(ApplicationService);
  private readonly jobsService = inject(JobService);
  private readonly chat = inject(ChatService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly auth = inject(AuthService);
  readonly ratings = inject(RatingService);
  readonly rows = signal<ApplicationRow[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly error = signal<TranslationKey | null>(null);
  readonly opening = signal<string | null>(null);
  readonly messageError = signal<{ id: string; key: TranslationKey } | null>(null);
  readonly retrying = signal<ReadonlySet<string>>(new Set());
  readonly startWithReview = signal(false);
  readonly selectedJobId = signal<string | null>(null);
  readonly icons = {
    search: LucideSearch,
    work: LucideBriefcaseBusiness,
    city: LucideMapPin,
    message: LucideMessageSquare,
    review: LucideStar,
    previous: LucideChevronLeft,
    next: LucideChevronRight,
  };

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('view') === 'jobs') {
      void this.router.navigate(['/jobs/my'], { replaceUrl: true });
      return;
    }
    this.load(0);
  }

  statusLabel(row: ApplicationRow): TranslationKey {
    return this.isCompleted(row)
      ? 'dashboard.status.COMPLETED'
      : `applications.status.${row.status}`;
  }

  statusHint(row: ApplicationRow): TranslationKey {
    return this.isCompleted(row) ? 'applications.workCompleted' : `applications.hint.${row.status}`;
  }

  private isCompleted(row: ApplicationRow): boolean {
    return row.status === 'ACCEPTED' && row.job?.status === 'COMPLETED';
  }

  number(value: number): string {
    return new Intl.NumberFormat(this.i18n.language()).format(value);
  }

  date(value: string): string {
    return new Intl.DateTimeFormat(this.i18n.language(), { dateStyle: 'medium' }).format(
      new Date(value),
    );
  }

  pageLabel(): string {
    return this.i18n.translate('applications.page', {
      page: this.number(this.page() + 1),
      total: this.number(this.totalPages()),
    });
  }

  canReview(row: ApplicationRow): boolean {
    return (
      row.job?.status === 'COMPLETED' && row.job.assignedWorkerId === this.auth.currentUserId()
    );
  }

  openReview(jobId: string): void {
    this.startWithReview.set(true);
    this.selectedJobId.set(jobId);
  }

  load(page = this.page()): void {
    if (this.loading() || page < 0 || (this.totalPages() > 0 && page >= this.totalPages())) return;
    this.loading.set(true);
    this.error.set(null);
    this.messageError.set(null);
    this.service
      .getMine(page)
      .pipe(
        switchMap((result) => {
          if (!result.content.length) return of({ ...result, content: [] });
          return forkJoin(
            result.content.map((application) => this.loadApplicationJob(application)),
          ).pipe(map((content) => ({ ...result, content })));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          this.rows.set(result.content);
          this.page.set(result.page);
          this.totalPages.set(result.totalPages);
          this.totalElements.set(result.totalElements);
          this.loading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.error.set(
            error.status === 0 || error.status === 503
              ? 'applications.connectionError'
              : 'applications.loadError',
          );
          this.loading.set(false);
        },
      });
  }

  private loadApplicationJob(application: JobApplication) {
    return this.jobsService.getJob(application.jobId).pipe(
      map((job) => ({ ...application, job, jobMissing: false })),
      catchError((error: HttpErrorResponse) =>
        of({ ...application, job: null, jobMissing: error.status === 404 }),
      ),
    );
  }

  retryJob(row: ApplicationRow): void {
    if (this.retrying().has(row.id)) return;
    this.retrying.update((ids) => new Set([...ids, row.id]));
    this.jobsService
      .getJob(row.jobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (job) => {
          this.rows.update((rows) =>
            rows.map((item) => (item.id === row.id ? { ...item, job, jobMissing: false } : item)),
          );
          this.finishRetry(row.id);
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 404)
            this.rows.update((rows) =>
              rows.map((item) => (item.id === row.id ? { ...item, jobMissing: true } : item)),
            );
          this.finishRetry(row.id);
        },
      });
  }

  private finishRetry(id: string): void {
    this.retrying.update((ids) => {
      const next = new Set(ids);
      next.delete(id);
      return next;
    });
  }

  message(row: ApplicationRow): void {
    if (this.opening() || row.status !== 'ACCEPTED') return;
    this.opening.set(row.id);
    this.messageError.set(null);
    this.chat
      .open(row.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (conversation) => {
          this.opening.set(null);
          void this.router.navigate(['/conversations', conversation.id]);
        },
        error: (error: HttpErrorResponse) => {
          this.opening.set(null);
          const code = error.error?.error?.code ?? error.error?.code;
          this.messageError.set({
            id: row.id,
            key:
              code === 'CONVERSATION_ACCESS_DENIED'
                ? 'applications.messageDenied'
                : 'applications.messageError',
          });
        },
      });
  }
}
