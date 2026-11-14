import { DatePipe, DecimalPipe, DOCUMENT } from '@angular/common';
import { Component, computed, DestroyRef, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ChartData, ChartOptions, LineController, LineElement, PointElement,
  CategoryScale, LinearScale, Tooltip, Legend,
} from 'chart.js';
import { BaseChartDirective, provideCharts } from 'ng2-charts';
import { Temporal } from '@js-temporal/polyfill';
import { Client } from '@stomp/stompjs';
import { auditTime, forkJoin, Subject, Subscription } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { AuthService } from '../../services/auth.js';
import { AnalyticsApi } from '../../services/analytics.js';
import { AnalyticsCount, AnalyticsQuery, AnalyticsResponse, Granularity } from '../../types/analytics.js';
import { TranslationKey } from '../../i18n/language.js';
import { environment } from '../../../environments/environment.js';

const TIME_ZONE = 'Africa/Casablanca';

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, BaseChartDirective, Navbar, Footer, TranslatePipe],
  providers: [provideCharts({
    registerables: [LineController, LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Legend],
  })],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly api = inject(AnalyticsApi);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly liveUpdates = new Subject<void>();
  private currentRequest?: Subscription;
  private socket: Client | null = null;

  // Keep form edits separate from the filters used by the displayed results.
  endDate = Temporal.Now.plainDateISO(TIME_ZONE).toString();
  startDate = Temporal.PlainDate.from(this.endDate).subtract({ days: 29 }).toString();
  granularity: Granularity = 'day';
  readonly appliedFilters = signal({ start: this.startDate, end: this.endDate, granularity: this.granularity });

  readonly analytics = signal<{ jobs: AnalyticsResponse; applications: AnalyticsResponse } | null>(null);
  readonly isLoading = signal(false);
  readonly loadFailed = signal(false);
  readonly filterError = signal<TranslationKey | null>(null);
  readonly lastUpdatedAt = signal<Date | null>(null);
  readonly connectionStatus = signal<'connecting' | 'connected' | 'disconnected' | 'error'>('disconnected');
  readonly connectionLabel = computed<TranslationKey>(() => `analytics.live.${this.connectionStatus()}`);

  readonly activityChart = computed<ChartData<'line', number[], string> | null>(() => {
    const analytics = this.analytics();
    if (!analytics) return null;

    const dates = this.createChartDates();
    const theme = getComputedStyle(this.document.documentElement);
    const jobsColor = theme.getPropertyValue('--color-primary-700').trim();
    const applicationsColor = theme.getPropertyValue('--color-secondary-700').trim();

    return {
      labels: dates,
      datasets: [
        {
          label: this.i18n.translate('analytics.jobs'),
          data: this.getCountsForDates(dates, analytics.jobs.activity),
          borderColor: jobsColor,
          backgroundColor: jobsColor,
        },
        {
          label: this.i18n.translate('analytics.applications'),
          data: this.getCountsForDates(dates, analytics.applications.activity),
          borderColor: applicationsColor,
          backgroundColor: applicationsColor,
        },
      ],
    };
  });

  readonly chartOptions = computed<ChartOptions<'line'>>(() => {
    const isRightToLeft = this.i18n.direction() === 'rtl';
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      plugins: {
        legend: { position: 'bottom', rtl: isRightToLeft },
        tooltip: { rtl: isRightToLeft },
      },
      scales: {
        x: { ticks: { maxRotation: 0, maxTicksLimit: 6 } },
        y: { beginAtZero: true, ticks: { precision: 0 } },
      },
    };
  });

  constructor() {
    this.liveUpdates.pipe(auditTime(250), takeUntilDestroyed()).subscribe(() => this.refreshAnalytics());

    effect(onCleanup => {
      // Clear the previous account's data whenever login or logout changes the session.
      this.auth.sessionRevision();
      this.clearDashboard();
      const token = this.auth.getToken();

      if (token) {
        // Applying filters should not reconnect the socket.
        untracked(() => this.refreshAnalytics());
        this.connectLiveUpdates(token);
      }

      onCleanup(() => {
        this.currentRequest?.unsubscribe();
        this.disconnectLiveUpdates();
      });
    });
  }

  applyFilters(): void {
    this.filterError.set(null);

    try {
      const start = Temporal.PlainDate.from(this.startDate);
      const end = Temporal.PlainDate.from(this.endDate);

      if (Temporal.PlainDate.compare(start, end) > 0) {
        this.filterError.set('analytics.invalidRange');
        return;
      }

      let numberOfPoints = start.until(end).days;
      if (this.granularity === 'week') {
        numberOfPoints /= 7;
      } else if (this.granularity === 'month') {
        numberOfPoints = start.until(end, { largestUnit: 'months' }).months;
      }

      if (numberOfPoints > 1200) {
        this.filterError.set('analytics.rangeTooLarge');
        return;
      }
    } catch {
      this.filterError.set('analytics.invalidRange');
      return;
    }

    this.appliedFilters.set({ start: this.startDate, end: this.endDate, granularity: this.granularity });
    this.analytics.set(null);
    this.lastUpdatedAt.set(null);
    this.refreshAnalytics();
  }

  refreshAnalytics(): void {
    const token = this.auth.getToken();
    if (!token) return;

    this.currentRequest?.unsubscribe();
    this.isLoading.set(true);
    this.loadFailed.set(false);

    const query = this.createQuery();
    this.currentRequest = forkJoin({
      jobs: this.api.jobs(query),
      applications: this.api.applications(query),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: analytics => {
        if (this.auth.getToken() !== token) return;
        this.analytics.set(analytics);
        this.lastUpdatedAt.set(new Date());
        this.isLoading.set(false);
      },
      error: () => {
        if (this.auth.getToken() !== token) return;
        this.loadFailed.set(true);
        this.isLoading.set(false);
      },
    });
  }

  private createQuery(): AnalyticsQuery {
    const { start, end, granularity } = this.appliedFilters();
    // Include the selected end date by stopping at midnight on the next day.
    const firstDay = Temporal.PlainDate.from(start);
    const dayAfterEnd = Temporal.PlainDate.from(end).add({ days: 1 });
    return {
      from: firstDay.toZonedDateTime(TIME_ZONE).toInstant().toString(),
      to: dayAfterEnd.toZonedDateTime(TIME_ZONE).toInstant().toString(),
      granularity,
    };
  }

  private createChartDates(): string[] {
    const { start, end, granularity } = this.appliedFilters();
    let currentDate = Temporal.PlainDate.from(start);
    const endDate = Temporal.PlainDate.from(end);
    let step: Temporal.DurationLike = { days: 1 };

    if (granularity === 'week') {
      currentDate = currentDate.subtract({ days: currentDate.dayOfWeek - 1 });
      step = { weeks: 1 };
    } else if (granularity === 'month') {
      currentDate = currentDate.with({ day: 1 });
      step = { months: 1 };
    }

    const dates: string[] = [];
    while (Temporal.PlainDate.compare(currentDate, endDate) <= 0) {
      dates.push(currentDate.toString());
      currentDate = currentDate.add(step);
    }
    return dates;
  }

  private getCountsForDates(dates: string[], activity: AnalyticsCount[]): number[] {
    const countsByDate = new Map<string, number>();
    for (const item of activity) {
      countsByDate.set(item.value.slice(0, 10), item.count);
    }
    // Successful responses omit dates with no activity; display those dates as zero.
    return dates.map(date => countsByDate.get(date) ?? 0);
  }

  private clearDashboard(): void {
    this.analytics.set(null);
    this.lastUpdatedAt.set(null);
    this.loadFailed.set(false);
    this.isLoading.set(false);
    this.connectionStatus.set('disconnected');
  }

  private connectLiveUpdates(token: string): void {
    const client = new Client({
      brokerURL: environment.analyticsSocketUrl || this.sameHostSocketUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: () => {
        if (this.auth.getToken() !== token) {
          void client.deactivate();
          return;
        }
        client.connectHeaders = { Authorization: `Bearer ${token}` };
      },
      onConnect: () => {
        if (this.socket !== client || this.auth.getToken() !== token) return;
        client.subscribe('/user/queue/analytics', frame => {
          if (this.socket !== client || this.auth.getToken() !== token) return;
          try {
            const message = JSON.parse(frame.body);
            if (message.resource !== 'jobs' && message.resource !== 'applications') {
              throw new Error('Invalid analytics message');
            }
            this.liveUpdates.next();
          } catch {
            this.connectionStatus.set('error');
            void client.deactivate();
          }
        });
        this.connectionStatus.set('connected');
        this.refreshAnalytics();
      },
      onWebSocketClose: () => {
        if (this.socket === client && this.connectionStatus() !== 'error') {
          this.connectionStatus.set('disconnected');
        }
      },
      onStompError: () => {
        if (this.socket === client) this.connectionStatus.set('error');
        void client.deactivate();
      },
    });

    this.socket = client;
    this.connectionStatus.set('connecting');
    client.activate();
  }

  private disconnectLiveUpdates(): void {
    const client = this.socket;
    this.socket = null;
    void client?.deactivate();
  }

  private sameHostSocketUrl(): string {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}/ws/analytics`;
  }
}
