import { NgTemplateOutlet, TitleCasePipe } from '@angular/common';
import {
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  OnChanges,
  OnDestroy,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { LucideSlidersHorizontal, LucideX } from '@lucide/angular';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { JobService } from '../../services/job.js';
import { JobFilters as JobFilterValues } from '../../types/job.js';
import {
  emptyFilters,
  jobDurationLabels,
  jobDurations,
  jobTimeLabels,
  jobTimes,
} from '../../utils/job.js';
import { Select } from '../select/select.js';

@Component({
  selector: 'app-job-filters',
  imports: [NgTemplateOutlet, FormsModule, TranslatePipe, Select, LucideSlidersHorizontal, LucideX],
  templateUrl: './job-filters.html',
  styleUrl: './job-filters.css',
})
export class JobFilters implements OnInit, OnChanges, OnDestroy {
  private readonly i18n = inject(I18nService);
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly titleCase = new TitleCasePipe();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly filters = input.required<JobFilterValues>();
  readonly filtersChange = output<JobFilterValues>();

  readonly draft = signal<JobFilterValues>(emptyFilters());

  readonly skills = signal<string[]>([]);
  readonly cities = signal<string[]>([]);

  readonly optionsError = signal(false);
  readonly budgetError = signal<TranslationKey | null>(null);
  readonly moreFilters = signal(false);

  readonly tradeOptions = computed(() => [
    // default
    {
      value: '',
      label: this.i18n.translate('jobs.allTrades'),
    },
    ...this.skills().map((value) => ({
      value,
      label: this.titleCase.transform(value.replaceAll('_', ' ')),
    })),
  ]);

  readonly cityOptions = computed(() => [
    // default
    {
      value: '',
      label: this.i18n.translate('directory.allCities'),
    },
    ...this.cities().map((value) => ({
      value,
      label: this.titleCase.transform(value.replaceAll('_', ' ')),
    })),
  ]);

  readonly durationOptions = computed(() => [
    // default
    {
      value: '',
      label: this.i18n.translate('jobs.anyDuration'),
    },
    ...jobDurations.map((value) => ({
      value,
      label: this.i18n.translate(jobDurationLabels[value]),
    })),
  ]);

  readonly timeOptions = computed(() => [
    // default
    {
      value: '',
      label: this.i18n.translate('jobs.anyTime'),
    },
    ...jobTimes.map((value) => ({
      value,
      label: this.i18n.translate(jobTimeLabels[value]),
    })),
  ]);

  readonly chips = computed(() => {
    const filters = this.filters();

    return (Object.keys(filters) as (keyof JobFilterValues)[])
      .filter((key) => filters[key] !== '' && filters[key] !== null)
      .map((key) => ({
        key,
        label: this.chipLabel(key, filters),
      }));
  });

  ngOnInit(): void {
    this.loadOptions();
  }

  ngOnChanges(): void {
    this.resetDraft();
  }

  ngOnDestroy(): void {
    this.dialog().nativeElement.close();
  }

  loadOptions(): void {
    this.optionsError.set(false);
    this.jobService
      .getSkills()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (skills) => this.skills.set(skills),
        error: () => this.optionsError.set(true),
      });

    this.jobService
      .getCities()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (cities) => this.cities.set(cities),
        error: () => this.optionsError.set(true),
      });
  }

  changeFilter(key: keyof JobFilterValues, value: string | number | null, defer = false): void {
    this.draft.update((filters) => ({ ...filters, [key]: value }));
    if (!defer) this.applyFilters();
  }

  applyFilters(): boolean {
    const filters = this.draft();
    const budgets = [filters.minBudget, filters.maxBudget];

    if (budgets.some((value) => value !== null && (!Number.isFinite(value) || value < 0))) {
      this.budgetError.set('jobs.invalidBudget');
      return false;
    }
    if (
      filters.minBudget !== null &&
      filters.maxBudget !== null &&
      filters.minBudget > filters.maxBudget
    ) {
      this.budgetError.set('jobs.budgetOrder');
      return false;
    }
    this.budgetError.set(null);
    // send filters
    this.filtersChange.emit({ ...filters });
    return true;
  }

  removeFilter(key: keyof JobFilterValues): void {
    this.draft.set({ ...this.filters(), [key]: emptyFilters()[key] });
    this.applyFilters();
  }

  clearFilters(defer = false): void {
    this.draft.set(emptyFilters());
    this.budgetError.set(null);
    if (!defer) this.applyFilters();
  }

  resetDraft(): void {
    this.draft.set({ ...this.filters() });
    this.budgetError.set(null);
  }

  openFilters(): void {
    this.resetDraft();
    this.dialog().nativeElement.showModal();
  }

  closeFilters(): void {
    this.dialog().nativeElement.close();
  }

  showResults(): void {
    if (this.applyFilters()) this.closeFilters();
  }

  private chipLabel(key: keyof JobFilterValues, filters: JobFilterValues): string {
    switch (key) {
      case 'minBudget':
        return this.i18n.translate('jobs.budgetFrom', { amount: filters.minBudget! });
      case 'maxBudget':
        return this.i18n.translate('jobs.budgetTo', { amount: filters.maxBudget! });
      case 'duration':
        return this.i18n.translate(
          filters.duration ? jobDurationLabels[filters.duration] : 'jobs.anyDuration',
        );
      case 'preferredTime':
        return this.i18n.translate(
          filters.preferredTime ? jobTimeLabels[filters.preferredTime] : 'jobs.anyTime',
        );
      default:
        return this.titleCase.transform(filters[key].replaceAll('_', ' '));
    }
  }
}
