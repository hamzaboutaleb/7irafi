import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, Subscription, switchMap } from 'rxjs';
import { UserService } from '../../services/user.js';
import { PublicProfessionalProfile } from '../../types/professional-profile.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { ApplicationService, applicationError } from '../../services/application.js';
import { JobService } from '../../services/job.js';
import { ChatService, chatError } from '../../services/chat.js';
import { ApplicationPage, JobApplication, applicationStatusLabels } from '../../types/application.js';
import { JobDetail } from '../../types/job.js';

@Component({
  selector: 'app-job-applicants',
  imports: [DatePipe, RouterLink, Navbar, Footer],
  templateUrl: './job-applicants.html',
})
export class JobApplicants implements OnInit {
  private readonly service = inject(ApplicationService);
  private readonly users = inject(UserService);
  private readonly jobsService = inject(JobService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly chat = inject(ChatService);
  private readonly destroyRef = inject(DestroyRef);
  private jobId = '';
  private request?: Subscription;
  readonly profiles = signal<Record<string, PublicProfessionalProfile | null>>({});
  readonly job = signal<JobDetail | null>(null);
  readonly result = signal<ApplicationPage | null>(null);
  readonly loading = signal(false);
  readonly accepting = signal<string | null>(null);
  readonly opening = signal<string | null>(null);
  readonly retryApplication = signal<JobApplication | null>(null);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly labels = applicationStatusLabels;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.jobId = params.get('jobId') ?? '';
      this.result.set(null);
      this.job.set(null);
      this.notice.set(null);
      this.retryApplication.set(null);
      this.accepting.set(null);
      this.load(0);
    });
  }

  load(page = this.result()?.page ?? 0): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.profiles.set({});
    this.request = forkJoin({
      job: this.jobsService.getJob(this.jobId),
      result: this.service.getApplicants(this.jobId, page),
    }).pipe(
      switchMap(({ job, result }) => {
        const ids = [...new Set(result.content.map(application => application.applicantId))];
        const requests = ids.map(id => this.users.getProfessional(id).pipe(
          map(response => [id, response.data] as const),
          catchError(() => of([id, null] as const)),
        ));
        return (requests.length ? forkJoin(requests) : of([])).pipe(
          map(entries => ({ job, result, profiles: Object.fromEntries(entries) })),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ job, result, profiles }) => {
        this.profiles.set(profiles);
        this.job.set(job);
        this.result.set(result);
        this.loading.set(false);
        if (this.retryApplication() && job.assignedWorkerId === this.retryApplication()?.applicantId) {
          this.retryApplication.set(null);
          this.notice.set('Applicant accepted.');
        }
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(applicationError(error, 'Unable to load applicants. Please try again.'));
        this.loading.set(false);
      },
    });
  }

  /**
   * Opens (or reopens) the conversation for this application. Chat starts at
   * application time, so negotiating before accepting is possible.
   */
  message(application: JobApplication): void {
    if (this.opening()) return;
    this.opening.set(application.id);
    this.notice.set(null);
    this.chat.open(application.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (conversation) => {
        this.opening.set(null);
        void this.router.navigate(['/conversations', conversation.id]);
      },
      error: (error: HttpErrorResponse) => {
        this.opening.set(null);
        this.notice.set(chatError(error, 'Unable to open the conversation. Please try again.'));
      },
    });
  }

  accept(application: JobApplication): void {
    if (this.loading() || this.accepting()) return;
    const retry = this.retryApplication();
    if (retry ? retry.id !== application.id :
      this.job()?.status !== 'OPEN' || application.status !== 'PENDING' ||
      !this.profiles()[application.applicantId]) return;
    this.accepting.set(application.id);
    this.notice.set(null);
    const jobId = this.jobId;
    this.service.accept(application.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        if (jobId !== this.jobId) return;
        this.accepting.set(null);
        this.retryApplication.set(null);
        this.notice.set('Applicant accepted.');
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        if (jobId !== this.jobId) return;
        this.accepting.set(null);
        if (error.status === 0 || error.status >= 500) {
          this.retryApplication.set(application);
          this.notice.set('Acceptance could not be confirmed. Refresh or retry the same applicant before choosing someone else.');
        } else {
          this.retryApplication.set(null);
          this.notice.set(error.status === 409
            ? 'This job has changed. The applicants list has been refreshed.'
            : applicationError(error, 'Unable to accept this applicant.'));
        }
        this.load();
      },
    });
  }
}
