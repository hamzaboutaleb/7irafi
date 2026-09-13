import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';

import { Footer } from '../../components/footer/footer.js';
import { JobCard } from '../../components/Job-card/job-card.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { JobService } from '../../services/job.js';
import { JobListItem } from '../../types/job.js';

@Component({
  selector: 'app-job',
  imports: [Navbar, Footer, JobCard, JobDetailsModal],
  templateUrl: './job.html',
})
export class Job implements OnInit {
  private readonly jobService = inject(JobService);

  readonly jobs = signal<JobListItem[]>([]);
  readonly selectedJobId = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadJobs();
  }

  loadJobs(pageNumber = this.currentPage()): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.jobService
      .getJobs({ page: pageNumber, size: 12 })
      .subscribe({
        next: (page) => {
          this.jobs.set(page.content);
          this.currentPage.set(page.page);
          this.totalPages.set(page.totalPages);
          this.isLoading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage.set(this.getErrorMessage(err));
          this.isLoading.set(false);
        },
      });
  }

  changePage(pageNumber: number): void {
    if (this.isLoading() || pageNumber < 0 || pageNumber >= this.totalPages()) {
      return;
    }

    this.loadJobs(pageNumber);
  }

  private getErrorMessage(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'The server cannot be reached. Make sure the API gateway is running.';
    }

    return err.error?.message ?? err.error?.error?.message ?? 'Unable to load jobs.';
  }
}
