import { Component, input, output } from '@angular/core';
import { LucideBriefcaseBusiness, LucideChevronLeft, LucideChevronRight } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { JobListItem } from '../../types/job.js';
import { JobCard } from '../Job-card/job-card.js';

@Component({
  selector: 'app-job-results',
  imports: [JobCard, TranslatePipe, LucideBriefcaseBusiness, LucideChevronLeft, LucideChevronRight],
  templateUrl: './job-results.html',
  host: { class: 'block' },
})
export class JobResults {
  readonly jobs = input.required<readonly JobListItem[]>();
  readonly isLoading = input(false);
  readonly errorMessage = input<string | null>(null);
  readonly hasFilters = input(false);
  readonly currentPage = input(0);
  readonly totalPages = input(0);

  readonly jobSelected = output<string>();
  readonly pageChange = output<number>();
  readonly retryRequested = output<void>();
  readonly clearFiltersRequested = output<void>();
}
