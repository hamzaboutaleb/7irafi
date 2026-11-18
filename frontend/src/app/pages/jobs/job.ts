import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, finalize, Subject, switchMap } from 'rxjs';

import { Footer } from '../../components/footer/footer.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { JobFilters } from '../../components/job-filters/job-filters.js';
import { JobResults } from '../../components/job-results/job-results.js';
import { JobSort } from '../../components/job-sort/job-sort.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { JobService } from '../../services/job.js';
import { JobListItem, JobFilters as JobFilterValues } from '../../types/job.js';
import { emptyFilters } from '../../utils/job.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';

@Component({
  selector: 'app-job',
  imports: [Navbar, Footer, JobDetailsModal, JobFilters, JobResults, JobSort, TranslatePipe],
  templateUrl: './job.html',
  styleUrl: './job.css',
})
export class Job implements OnInit {
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly i18n = inject(I18nService);
  private readonly loadRequests = new Subject<number>();

  readonly jobs = signal<JobListItem[]>([]);
  readonly selectedJobId = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly filters = signal<JobFilterValues>(emptyFilters());
  readonly hasFilters = computed(() =>
    Object.values(this.filters()).some((value) => value !== '' && value !== null),
  );

  readonly sort = signal('createdAt,desc');

  applyFilters(filters: JobFilterValues): void {
    this.filters.set(filters);
    this.loadJobs(0);
  }

  clearFilters(): void {
    this.applyFilters(emptyFilters());
  }

  changeSort(value: string): void {
    this.sort.set(value);
    this.loadJobs(0);
  }

  ngOnInit(): void {
    this.loadRequests
      .pipe(
        switchMap((pageNumber) => {
          this.isLoading.set(true);
          this.errorMessage.set(null);
          const filters = this.filters();
          return this.jobService
            .getJobs({
              ...filters,
              duration: filters.duration || null,
              preferredTime: filters.preferredTime || null,
              sort: this.sort(),
              page: pageNumber,
              size: 12,
            })
            .pipe(
              catchError((err: HttpErrorResponse) => {
                this.errorMessage.set(this.getErrorMessage(err));
                return EMPTY;
              }),
              finalize(() => this.isLoading.set(false)),
            );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => {
        this.jobs.set(page.content);
        this.currentPage.set(page.page);
        this.totalPages.set(page.totalPages);
      });
    this.loadJobs();
  }

  loadJobs(pageNumber = this.currentPage()): void {
    this.loadRequests.next(pageNumber);
  }

  changePage(pageNumber: number): void {
    if (this.isLoading() || pageNumber < 0 || pageNumber >= this.totalPages()) {
      return;
    }

    this.loadJobs(pageNumber);
  }

  private getErrorMessage(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return this.i18n.translate('jobs.networkError');
    }
    return err.error?.message ?? err.error?.error?.message ?? this.i18n.translate('jobs.loadError');
  }
}
