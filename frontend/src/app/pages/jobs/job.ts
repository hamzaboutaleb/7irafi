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
import { Select } from '../../components/select/select.js';
import { I18nService } from '../../services/i18n.js';
import { TranslationKey } from '../../i18n/language.js';
import { LucideSlidersHorizontal, LucideX, LucideBriefcaseBusiness, LucideChevronLeft, LucideChevronRight } from '@lucide/angular';

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
  imports: [FormsModule, NgTemplateOutlet, Navbar, Footer, JobCard, JobDetailsModal, PostingStatusModal, TranslatePipe, Select, LucideSlidersHorizontal, LucideX, LucideBriefcaseBusiness, LucideChevronLeft, LucideChevronRight],
  templateUrl: './job.html',
})
export class Job implements OnInit {
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly i18n = inject(I18nService);
  private readonly titleCase = new TitleCasePipe();
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
  readonly budgetError = signal<TranslationKey | null>(null);
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
    { value: 'HALF_DAY', label: 'createJob.halfDay' },
    { value: 'FULL_DAY', label: 'createJob.fullDay' },
    { value: 'MULTI_DAY', label: 'createJob.multipleDays' },
  ] as const;
  readonly times = [
    { value: 'MORNING', label: 'createJob.morning' },
    { value: 'AFTERNOON', label: 'createJob.afternoon' },
    { value: 'EVENING', label: 'createJob.evening' },
    { value: 'ANYTIME', label: 'createJob.anytime' },
  ] as const;
  readonly tradeOptions = computed(() => [
    { value: '', label: this.i18n.translate('jobs.allTrades') },
    ...this.skills().map(value => ({ value, label: this.titleCase.transform(value.replaceAll('_', ' ')) })),
  ]);
  readonly cityOptions = computed(() => [
    { value: '', label: this.i18n.translate('directory.allCities') },
    ...this.cities().map(value => ({ value, label: this.titleCase.transform(value.replaceAll('_', ' ')) })),
  ]);
  readonly durationOptions = computed(() => [
    { value: '', label: this.i18n.translate('jobs.anyDuration') },
    ...this.durations.map(option => ({ value: option.value, label: this.i18n.translate(option.label) })),
  ]);
  readonly timeOptions = computed(() => [
    { value: '', label: this.i18n.translate('jobs.anyTime') },
    ...this.times.map(option => ({ value: option.value, label: this.i18n.translate(option.label) })),
  ]);
  readonly sortOptions = computed(() => [
    { value: 'createdAt,desc', label: this.i18n.translate('jobs.newest') },
    { value: 'budget,desc', label: this.i18n.translate('jobs.budgetHigh') },
    { value: 'budget,asc', label: this.i18n.translate('jobs.budgetLow') },
  ]);
  readonly chips = computed(() => {
    const f = this.filters();
    return (Object.keys(f) as (keyof JobFilters)[])
      .filter(key => f[key] !== '' && f[key] !== null)
      .map(key => ({ key, label: key === 'minBudget' ? this.i18n.translate('jobs.budgetFrom', { amount: f[key]! })
        : key === 'maxBudget' ? this.i18n.translate('jobs.budgetTo', { amount: f[key]! })
        : key === 'duration' ? this.durationOptions().find(option => option.value === f[key])!.label
        : key === 'preferredTime' ? this.timeOptions().find(option => option.value === f[key])!.label
        : this.titleCase.transform(String(f[key]).replaceAll('_', ' ')) }));
  });

  updateFilter(key: keyof JobFilters, value: string | number | null, mobile: boolean): void {
    this.draft.update(filters => ({ ...filters, [key]: value }));
    if (!mobile) this.applyFilters();
  }

  applyFilters(): boolean {
    const f = this.draft();
    const budgets = [f.minBudget, f.maxBudget];
    if (budgets.some(value => value !== null && (!Number.isFinite(value) || value < 0))) {
      this.budgetError.set('jobs.invalidBudget');
      return false;
    }
    if (f.minBudget !== null && f.maxBudget !== null && f.minBudget > f.maxBudget) {
      this.budgetError.set('jobs.budgetOrder');
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
      return this.i18n.translate('jobs.networkError');
    }

    return err.error?.message ?? err.error?.error?.message ?? this.i18n.translate('jobs.loadError');
  }
}
