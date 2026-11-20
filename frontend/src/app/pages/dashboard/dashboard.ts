import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { auditTime, filter, forkJoin, Subscription } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { AuthService } from '../../services/auth.js';
import { AnalyticsApi } from '../../services/analytics.js';
import { AnalyticsCount, AnalyticsResponse } from '../../types/analytics.js';
import { TranslationKey } from '../../i18n/language.js';
import { DashboardActivity } from './components/dashboard-activity/dashboard-activity.js';
import { LiveAnalyticsConnection } from './live-analytics-connection.js';
import {
  buildAnalyticsQuery,
  buildBucketDates,
  createDefaultFilters,
  type DashboardFilters,
  validateDashboardFilters,
} from './utils/dashboard-date-range.js';

@Component({
  selector: 'app-dashboard',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    DashboardActivity,
    Navbar,
    Footer,
    TranslatePipe,
  ],
  providers: [LiveAnalyticsConnection],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly analyticsApi = inject(AnalyticsApi);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly liveConnection = inject(LiveAnalyticsConnection);
  private analyticsSubscription?: Subscription;

  protected readonly draftFilters = createDefaultFilters();
  protected readonly appliedFilters = signal<DashboardFilters>({ ...this.draftFilters });

  protected readonly analyticsResults = signal<{
    jobs: AnalyticsResponse;
    applications: AnalyticsResponse;
  } | null>(null);
  protected readonly requestStatus = signal<'idle' | 'loading' | 'error'>('idle');
  protected readonly filterErrorKey = signal<TranslationKey | null>(null);
  protected readonly lastUpdatedAt = signal<Date | null>(null);

  protected readonly connectionStatus = this.liveConnection.status;
  protected readonly connectionLabelKey = computed<TranslationKey>(
    () => `analytics.live.${this.connectionStatus()}`,
  );

  protected readonly activityRows = computed(() => {
    const results = this.analyticsResults();

    if (!results) return null;

    const jobsByDate = indexActivityCountsByDate(results.jobs.activity);
    const applicationsByDate = indexActivityCountsByDate(results.applications.activity);

    return buildBucketDates(this.appliedFilters()).map((date) => ({
      date,
      jobs: jobsByDate.get(date) ?? 0,
      applications: applicationsByDate.get(date) ?? 0,
    }));
  });

  constructor() {
    this.liveConnection.updates$
      .pipe(
        filter((event) => event === 'connected'),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.refreshAnalytics());

    this.liveConnection.updates$
      .pipe(
        filter((event) => event === 'activity'),
        auditTime(250),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.refreshAnalytics());

    toObservable(this.auth.sessionRevision)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.handleSessionChange());
  }

  protected applyFilters(): void {
    const filtersToApply = { ...this.draftFilters };
    const errorKey = validateDashboardFilters(filtersToApply);
    this.filterErrorKey.set(errorKey);

    if (errorKey) return;

    this.appliedFilters.set(filtersToApply);
    this.clearAnalyticsResults();
    this.refreshAnalytics();
  }

  protected refreshAnalytics(): void {
    const token = this.auth.getToken();

    if (!token) return;

    this.analyticsSubscription?.unsubscribe();
    this.requestStatus.set('loading');

    const query = buildAnalyticsQuery(this.appliedFilters());

    this.analyticsSubscription = forkJoin({
      jobs: this.analyticsApi.jobs(query),
      applications: this.analyticsApi.applications(query),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => {
          if (this.auth.getToken() !== token) return;

          this.analyticsResults.set(results);
          this.lastUpdatedAt.set(new Date());
          this.requestStatus.set('idle');
        },
        error: () => {
          if (this.auth.getToken() !== token) return;

          this.requestStatus.set('error');
        },
      });
  }

  private handleSessionChange(): void {
    this.analyticsSubscription?.unsubscribe();
    this.liveConnection.disconnect();
    this.clearAnalyticsResults();
    this.requestStatus.set('idle');

    const token = this.auth.getToken();

    if (!token) return;

    this.refreshAnalytics();
    this.liveConnection.connect(token);
  }

  private clearAnalyticsResults(): void {
    this.analyticsResults.set(null);
    this.lastUpdatedAt.set(null);
  }
}

function indexActivityCountsByDate(activity: readonly AnalyticsCount[]): Map<string, number> {
  const countsByDate = new Map<string, number>();

  for (const item of activity) {
    countsByDate.set(item.value.slice(0, 10), item.count);
  }

  return countsByDate;
}
