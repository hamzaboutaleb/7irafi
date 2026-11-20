import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import {
  CategoryScale,
  ChartData,
  ChartDataset,
  ChartOptions,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';
import { BaseChartDirective, provideCharts } from 'ng2-charts';
import { TranslationKey } from '../../../../i18n/language.js';
import { TranslatePipe } from '../../../../pipes/translate-pipe.js';
import { I18nService } from '../../../../services/i18n.js';

interface ActivityRow {
  date: string;
  jobs: number;
  applications: number;
}

@Component({
  selector: 'app-dashboard-activity',
  imports: [BaseChartDirective, TranslatePipe],
  providers: [
    provideCharts({
      registerables: [
        LineController,
        LineElement,
        PointElement,
        CategoryScale,
        LinearScale,
        Tooltip,
        Legend,
      ],
    }),
  ],
  templateUrl: './dashboard-activity.html',
  styleUrl: './dashboard-activity.css',
})
export class DashboardActivity {
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);

  readonly rows = input.required<readonly ActivityRow[]>();

  protected readonly activityChart = computed<ChartData<'line', number[], string>>(() => {
    const rows = this.rows();

    const theme = getComputedStyle(this.document.documentElement);
    const jobsColor = theme.getPropertyValue('--color-primary-700').trim();
    const applicationsColor = theme.getPropertyValue('--color-secondary-700').trim();

    return {
      labels: rows.map((row) => row.date),
      datasets: [
        this.buildActivityDataset(
          'analytics.jobs',
          rows.map((row) => row.jobs),
          jobsColor,
        ),
        this.buildActivityDataset(
          'analytics.applications',
          rows.map((row) => row.applications),
          applicationsColor,
        ),
      ],
    };
  });

  protected readonly chartOptions = computed<ChartOptions<'line'>>(() => {
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

  private buildActivityDataset(
    labelKey: TranslationKey,
    counts: number[],
    color: string,
  ): ChartDataset<'line', number[]> {
    return {
      label: this.i18n.translate(labelKey),
      data: counts,
      borderColor: color,
      backgroundColor: color,
    };
  }
}
