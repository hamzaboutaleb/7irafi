import { TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, Subscription, switchMap } from 'rxjs';
import { LucideArrowLeft, LucideBriefcaseBusiness, LucideMapPin, LucideUsersRound, LucideUserRound, LucideWrench, LucideChevronDown, LucideChevronLeft, LucideChevronRight, LucideMessageSquare, LucideCheck } from '@lucide/angular';
import { UserService } from '../../services/user.js';
import { I18nService } from '../../services/i18n.js';
import { PublicProfessionalProfile } from '../../types/professional-profile.js';
import { PublicUserProfile } from '../../types/user.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { ApplicationService } from '../../services/application.js';
import { JobService } from '../../services/job.js';
import { ChatService } from '../../services/chat.js';
import { ApplicationPage, ApplicationStatus, JobApplication } from '../../types/application.js';
import { JobDetail } from '../../types/job.js';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-job-applicants',
  imports: [TitleCasePipe, RouterLink, Navbar, Footer, TranslatePipe, LucideArrowLeft, LucideBriefcaseBusiness, LucideMapPin, LucideUsersRound, LucideUserRound, LucideWrench, LucideChevronDown, LucideChevronLeft, LucideChevronRight, LucideMessageSquare, LucideCheck],
  templateUrl: './job-applicants.html',
  styleUrl: './job-applicants.css',
})
export class JobApplicants implements OnInit {
  private readonly service = inject(ApplicationService);
  private readonly users = inject(UserService);
  private readonly jobsService = inject(JobService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly chat = inject(ChatService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);
  private jobId = '';
  private request?: Subscription;
  readonly profiles = signal<Record<string, PublicProfessionalProfile | null>>({});
  readonly people = signal<Record<string, PublicUserProfile | null>>({});
  readonly expanded = signal<ReadonlySet<string>>(new Set());
  readonly refreshingProfiles = signal<ReadonlySet<string>>(new Set());
  private readonly failedImages = signal<ReadonlySet<string>>(new Set());
  readonly job = signal<JobDetail | null>(null);
  readonly result = signal<ApplicationPage | null>(null);
  readonly loading = signal(false);
  readonly accepting = signal<string | null>(null);
  readonly opening = signal<string | null>(null);
  readonly retryApplication = signal<JobApplication | null>(null);
  readonly error = signal<TranslationKey | null>(null);
  readonly notice = signal<TranslationKey | null>(null);
  readonly noticeTone = signal<'info' | 'success' | 'warning' | 'error'>('info');
  readonly messageError = signal<{ id: string; key: TranslationKey } | null>(null);
  readonly budgetLabel = computed(() => new Intl.NumberFormat(this.i18n.language(), { maximumFractionDigits: 2 }).format(this.job()?.budget ?? 0));
  readonly jobStatusLabel = computed(() => {
    const keys = { OPEN: 'dashboard.status.OPEN', ASSIGNED: 'dashboard.status.ASSIGNED', COMPLETED: 'dashboard.status.COMPLETED', CANCELLED: 'dashboard.status.CANCELLED' } as const;
    return this.i18n.translate(keys[this.job()?.status ?? 'OPEN']);
  });

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.jobId = params.get('jobId') ?? '';
      this.result.set(null);
      this.job.set(null);
      this.notice.set(null);
      this.retryApplication.set(null);
      this.accepting.set(null);
      this.opening.set(null);
      this.messageError.set(null);
      this.expanded.set(new Set());
      this.refreshingProfiles.set(new Set());
      this.load(0);
    });
  }

  nameFor(id: string): string {
    const person = this.people()[id];
    return (person ? `${person.firstName} ${person.lastName}`.trim() : this.profiles()[id]?.firstName) || this.i18n.translate('chat.applicant');
  }

  initialsFor(id: string): string {
    const person = this.people()[id];
    return (person ? person.firstName.charAt(0) + person.lastName.charAt(0) : this.profiles()[id]?.firstName.charAt(0) ?? '').toUpperCase();
  }

  avatarFor(id: string): string | null {
    return this.failedImages().has(id) ? null : this.people()[id]?.avatarUrl || this.profiles()[id]?.avatarUrl || null;
  }

  imageFailed(id: string): void { this.failedImages.update(current => new Set([...current, id])); }

  dateLabel(value: string): string {
    return new Intl.DateTimeFormat(this.i18n.language(), { dateStyle: 'medium' }).format(new Date(value));
  }

  statusLabel(status: ApplicationStatus): string {
    const keys = { PENDING: 'applicants.pending', ACCEPTED: 'dashboard.status.ACCEPTED', NOT_SELECTED: 'dashboard.status.NOT_SELECTED', CLOSED: 'dashboard.status.CLOSED' } as const;
    return this.i18n.translate(keys[status]);
  }

  toggleBiography(id: string): void {
    this.expanded.update(current => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  private profileRequest(id: string) {
    return forkJoin({
      professional: this.users.getProfessional(id).pipe(map(response => response.data), catchError(() => of(null))),
      person: this.users.getPublicUserProfile(id).pipe(map(response => response.data), catchError(() => of(null))),
    }).pipe(map(data => ({ id, ...data })));
  }

  retryProfile(id: string): void {
    if (this.loading() || this.accepting() || this.refreshingProfiles().has(id)) return;
    const jobId = this.jobId;
    this.refreshingProfiles.update(current => new Set([...current, id]));
    this.profileRequest(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(data => {
      if (jobId !== this.jobId) return;
      this.profiles.update(current => ({ ...current, [id]: data.professional }));
      this.people.update(current => ({ ...current, [id]: data.person }));
      this.refreshingProfiles.update(current => { const next = new Set(current); next.delete(id); return next; });
      this.failedImages.update(current => { const next = new Set(current); next.delete(id); return next; });
    });
  }

  load(page = this.result()?.page ?? 0): void {
    if (page < 0 || this.accepting()) return;
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.profiles.set({});
    this.people.set({});
    this.failedImages.set(new Set());
    this.request = forkJoin({
      job: this.jobsService.getJob(this.jobId),
      result: this.service.getApplicants(this.jobId, page),
    }).pipe(
      switchMap(({ job, result }) => {
        const ids = [...new Set(result.content.map(application => application.applicantId))];
        return (ids.length ? forkJoin(ids.map(id => this.profileRequest(id))) : of([])).pipe(
          map(entries => ({ job, result, profiles: Object.fromEntries(entries.map(entry => [entry.id, entry.professional])), people: Object.fromEntries(entries.map(entry => [entry.id, entry.person])) })),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ job, result, profiles, people }) => {
        this.profiles.set(profiles);
        this.people.set(people);
        this.job.set(job);
        this.result.set(result);
        this.loading.set(false);
        if (this.retryApplication() && job.assignedWorkerId === this.retryApplication()?.applicantId) {
          this.retryApplication.set(null);
          this.notice.set('applicants.accepted');
          this.noticeTone.set('success');
        }
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(this.errorKey(error, 'applicants.loadFailed'));
        this.loading.set(false);
      },
    });
  }

  message(application: JobApplication): void {
    if (this.opening()) return;
    const jobId = this.jobId;
    this.opening.set(application.id);
    this.messageError.set(null);
    this.chat.open(application.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: conversation => {
        if (jobId !== this.jobId) return;
        this.opening.set(null);
        void this.router.navigate(['/conversations', conversation.id]);
      },
      error: (error: HttpErrorResponse) => {
        if (jobId !== this.jobId) return;
        this.opening.set(null);
        this.messageError.set({ id: application.id, key: this.errorKey(error, 'applicants.messageFailed') });
      },
    });
  }

  accept(application: JobApplication): void {
    if (this.loading() || this.accepting()) return;
    const retry = this.retryApplication();
    if (retry ? retry.id !== application.id :
      this.job()?.status !== 'OPEN' || application.status !== 'PENDING' || !this.profiles()[application.applicantId]) return;
    this.accepting.set(application.id);
    if (!retry) this.notice.set(null);
    const jobId = this.jobId;
    this.service.accept(application.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        if (jobId !== this.jobId) return;
        this.accepting.set(null);
        this.retryApplication.set(null);
        this.notice.set('applicants.accepted');
        this.noticeTone.set('success');
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        if (jobId !== this.jobId) return;
        this.accepting.set(null);
        if (error.status === 0 || error.status >= 500) {
          this.retryApplication.set(application);
          this.notice.set('applicants.acceptUnconfirmed');
          this.noticeTone.set('warning');
        } else {
          this.retryApplication.set(null);
          this.notice.set(error.status === 409 ? 'applicants.changed' : this.errorKey(error, 'applicants.acceptFailed'));
          this.noticeTone.set(error.status === 409 ? 'info' : 'error');
        }
        this.load();
      },
    });
  }

  private errorKey(error: HttpErrorResponse, fallback: TranslationKey): TranslationKey {
    if (error.status === 0 || error.status === 503) return 'jobDetails.serviceUnavailable';
    const messages: Record<string, TranslationKey> = {
      WORKER_REQUIRED: 'jobDetails.needsProfile', OWN_JOB_APPLICATION: 'jobDetails.ownJob', JOB_NOT_OPEN: 'jobDetails.closed',
      JOB_NOT_FOUND: 'jobDetails.noLongerAvailable', JOB_ACCESS_DENIED: 'jobDetails.accessDenied', APPLICATION_NOT_FOUND: 'jobDetails.applicationMissing',
      CONVERSATION_ACCESS_DENIED: 'applicants.messageDenied', CONVERSATION_NOT_FOUND: 'applicants.messageUnavailable', APPLICATION_SERVICE_UNAVAILABLE: 'jobDetails.serviceUnavailable',
    };
    return messages[error.error?.code ?? error.error?.error?.code] ?? fallback;
  }
}
