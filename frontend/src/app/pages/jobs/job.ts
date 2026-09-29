import { DOCUMENT, NgTemplateOutlet, TitleCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';

import { Footer } from '../../components/footer/footer.js';
import { JobCard } from '../../components/Job-card/job-card.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { JobService } from '../../services/job.js';
import { EstimatedDuration, JobListItem, PreferredTime } from '../../types/job.js';
import { JobPostingResult } from '../../services/job-posting-result.js';
import { PostingStatusModal } from '../../components/posting-status-modal/posting-status-modal.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

interface JobFilters {
  skill: string;
  city: string;
  minBudget: number | null;
  maxBudget: number | null;
  duration: EstimatedDuration | '';
  preferredTime: PreferredTime | '';
}

const emptyFilters = (): JobFilters => ({
  skill: '', city: '', minBudget: null, maxBudget: null, duration: '', preferredTime: '',
});

@Component({
  selector: 'app-job',
  imports: [FormsModule, NgTemplateOutlet, TitleCasePipe, Navbar, Footer, JobCard, JobDetailsModal, PostingStatusModal, TranslatePipe],
  templateUrl: './job.html',
  styleUrl: './job.css',
})
export class Job implements OnInit {
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  readonly postingResult = inject(JobPostingResult);
  readonly photoErrors = computed(() => this.postingResult.result()?.failed.map(
    (photo) => `${photo.file.name}: ${photo.error}`,
  ) ?? []);

  retryPhotos(): void {
    this.postingResult.retry(() => {
      if (!this.destroyRef.destroyed) this.loadJobs();
    });
  }

  readonly jobs = signal<JobListItem[]>([]);
  readonly selectedJobId = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly skills = signal<string[]>([]);
  readonly cities = signal<string[]>([]);
  readonly optionsError = signal(false);
  readonly filters = signal<JobFilters>(emptyFilters());
  readonly draft = signal<JobFilters>(emptyFilters());
  readonly sort = signal('createdAt,desc');
  readonly totalJobs = signal(0);
  readonly moreFilters = signal(false);
  readonly budgetError = signal<string | null>(null);
  readonly filterDialog = viewChild.required<ElementRef<HTMLDialogElement>>('filterDialog');
  private request?: Subscription;
  private readonly document = inject(DOCUMENT);
  private previousOverflow: string | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.restoreScroll());
  }

  private restoreScroll(): void {
    if (this.previousOverflow !== null) {
      this.document.body.style.overflow = this.previousOverflow;
      this.previousOverflow = null;
    }
  }
  readonly durations = [
    { value: 'HALF_DAY', label: 'Half day' },
    { value: 'FULL_DAY', label: 'Full day' },
    { value: 'MULTI_DAY', label: 'Multiple days' },
  ];
  readonly times = ['MORNING', 'AFTERNOON', 'EVENING', 'ANYTIME'];
  readonly chips = computed(() => {
    const f = this.filters();
    return (Object.keys(f) as (keyof JobFilters)[])
      .filter(key => f[key] !== '' && f[key] !== null)
      .map(key => ({ key, label: key === 'minBudget' ? `From ${f[key]} MAD`
        : key === 'maxBudget' ? `Up to ${f[key]} MAD`
        : key === 'duration' ? this.durations.find(d => d.value === f[key])!.label
        : String(f[key]).replaceAll('_', ' ').toLowerCase() }));
  });

  updateFilter(key: keyof JobFilters, value: string | number | null, mobile: boolean): void {
    this.draft.update(filters => ({ ...filters, [key]: value }));
    if (!mobile) this.applyFilters();
  }

  applyFilters(): boolean {
    const f = this.draft();
    const budgets = [f.minBudget, f.maxBudget];
    if (budgets.some(value => value !== null && (!Number.isFinite(value) || value < 0))) {
      this.budgetError.set('Enter a valid budget of 0 MAD or more.');
      return false;
    }
    if (f.minBudget !== null && f.maxBudget !== null && f.minBudget > f.maxBudget) {
      this.budgetError.set('Maximum budget must be at least the minimum.');
      return false;
    }
    this.budgetError.set(null);
    this.filters.set({ ...f });
    this.loadJobs(0);
    return true;
  }

  removeFilter(key: keyof JobFilters): void {
    this.draft.set({ ...this.filters(), [key]: emptyFilters()[key] });
    this.applyFilters();
  }

  clearFilters(mobile = false): void {
    this.draft.set(emptyFilters());
    this.budgetError.set(null);
    if (!mobile) this.applyFilters();
  }

  openFilters(): void {
    this.draft.set({ ...this.filters() });
    this.budgetError.set(null);
    this.previousOverflow = this.document.body.style.overflow;
    this.document.body.style.overflow = 'hidden';
    this.filterDialog().nativeElement.showModal();
  }

  closeFilters(): void {
    this.filterDialog().nativeElement.close();
    this.restoreScroll();
    this.draft.set({ ...this.filters() });
    this.budgetError.set(null);
  }

  showResults(): void {
    if (this.applyFilters()) this.closeFilters();
  }

  changeSort(value: string): void {
    this.sort.set(value);
    this.loadJobs(0);
  }

  loadOptions(): void {
    this.optionsError.set(false);
    this.jobService.getSkills().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: skills => this.skills.set(skills), error: () => this.optionsError.set(true),
    });
    this.jobService.getCities().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: cities => this.cities.set(cities), error: () => this.optionsError.set(true),
    });
  }

  ngOnInit(): void {
    this.loadOptions();
    this.postingResult.clearIfSessionChanged();
    this.loadJobs();
  }

  loadJobs(pageNumber = this.currentPage()): void {
    this.request?.unsubscribe();
    this.isLoading.set(true);
    this.errorMessage.set(null);

    const filters = this.filters();
    this.request = this.jobService
      .getJobs({ ...filters, duration: filters.duration || null,
        preferredTime: filters.preferredTime || null,
        sort: this.sort(), page: pageNumber, size: 12 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.jobs.set(page.content);
          this.totalJobs.set(page.totalElements);
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
