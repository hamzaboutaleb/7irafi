import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { Footer } from '../../components/footer/footer.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { MyJobsResults } from '../../components/my-jobs-results/my-jobs-results.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { JobService } from '../../services/job.js';
import { RatingService } from '../../services/rating.js';
import { JobActionResult, JobResponse, JobStatus } from '../../types/job.js';
import { jobStatuses, jobStatusLabels } from '../../utils/job.js';

@Component({
  selector: 'app-my-jobs',
  imports: [Navbar, Footer, MyJobsResults, JobDetailsModal, TranslatePipe],
  templateUrl: './my-jobs.html',
  styleUrl: './my-jobs.css',
})
export class MyJobs implements OnInit {
  private readonly i18n = inject(I18nService);
  private readonly service = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);

  readonly ratings = inject(RatingService);

  readonly jobs = signal<JobResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly completedJobToReview = signal<JobResponse | null>(null);
  readonly status = signal<JobStatus | ''>('');
  readonly statuses: (JobStatus | '')[] = ['', ...jobStatuses];

  readonly visibleJobs = computed(() =>
    this.jobs().filter((job) => !this.status() || job.status === this.status()),
  );

  readonly jobModal = signal<{ jobId: string; startWithReview: boolean } | null>(null);

  ngOnInit(): void {
    this.load();
  }

  statusLabel(status: JobStatus | ''): TranslationKey {
    return status ? jobStatusLabels[status] : 'myJobs.all';
  }

  openReview(jobId: string): void {
    this.jobModal.set({ jobId, startWithReview: true });
  }

  load(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    this.service
      .getMyJobs()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (jobs) => {
          this.jobs.set([...jobs].sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
        },
        error: (error: HttpErrorResponse) => {
          this.error.set(
            this.i18n.translate(error.status === 0 ? 'myJobs.connectionError' : 'myJobs.loadError'),
          );
        },
      });
  }

  actionCompleted(result: JobActionResult): void {
    this.jobs.update((jobs) =>
      result.action === 'delete'
        ? jobs.filter((job) => job.id !== result.jobId)
        : jobs.map((job) => (job.id === result.job.id ? result.job : job)),
    );
    if (result.action === 'complete') this.completedJobToReview.set(result.job);
  }
}
