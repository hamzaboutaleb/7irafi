import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { JobCard } from '../../components/Job-card/job-card.js';
import { ApplicationService, applicationError } from '../../services/application.js';
import { JobService } from '../../services/job.js';
import { JobApplication, applicationStatusLabels } from '../../types/application.js';
import { JobListItem, JobResponse } from '../../types/job.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

interface ApplicationRow extends JobApplication {
  job: JobListItem | null;
  jobTitle: string;
  jobMissing: boolean;
}

@Component({
  selector: 'app-applications',
  imports: [DatePipe, RouterLink, Navbar, Footer, JobDetailsModal, JobCard, TranslatePipe],
  templateUrl: './applications.html',
})
export class Applications implements OnInit {
  private readonly service = inject(ApplicationService);
  private readonly jobsService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  readonly view = signal<'applications' | 'jobs'>('applications');
  readonly rows = signal<ApplicationRow[]>([]);
  readonly jobs = signal<JobResponse[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly selectedJobId = signal<string | null>(null);
  readonly labels = applicationStatusLabels;

  ngOnInit(): void {
    this.show(this.route.snapshot.queryParamMap.get('view') === 'jobs' ? 'jobs' : 'applications');
  }

  show(view: 'applications' | 'jobs'): void {
    if (this.loading()) return;
    this.view.set(view);
    this.load(0);
  }

  load(page = this.page()): void {
    if (this.loading() || page < 0) return;
    this.loading.set(true);
    this.error.set(null);
    if (this.view() === 'jobs') {
      this.jobsService.getMyJobs().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (jobs) => {
          this.jobs.set(jobs);
          this.loading.set(false);
        },
        error: (error: HttpErrorResponse) => this.failed(error),
      });
      return;
    }
    this.service.getMine(page).pipe(
      switchMap((result) => {
        const details = result.content.map((application) => this.jobsService.getJob(application.jobId).pipe(
          map((job) => ({
            ...application,
            job: { ...job, thumbnailUrl: job.images[0]?.url ?? null },
            jobTitle: job.title,
            jobMissing: false,
          })),
          catchError((error: HttpErrorResponse) => of({
            ...application,
            job: null,
            jobTitle: error.status === 404 ? 'Job no longer available' : 'Job details unavailable',
            jobMissing: error.status === 404,
          })),
        ));
        return (details.length ? forkJoin(details) : of([])).pipe(map((rows) => ({ result, rows })));
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ result, rows }) => {
        this.rows.set(rows);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => this.failed(error),
    });
  }

  private failed(error: HttpErrorResponse): void {
    this.error.set(applicationError(error, 'Unable to load your list. Please try again.'));
    this.loading.set(false);
  }
}
