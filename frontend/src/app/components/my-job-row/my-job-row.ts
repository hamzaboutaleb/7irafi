import { TitleCasePipe } from '@angular/common';
import { Component, computed, inject, input, output } from '@angular/core';
import { LucideBriefcaseBusiness, LucideMapPin } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { JobActionResult, JobResponse } from '../../types/job.js';
import { MyJobActions } from '../my-job-actions/my-job-actions.js';
import { jobStatusLabels } from '../../utils/job.js';

@Component({
  selector: 'app-my-job-row',
  imports: [TitleCasePipe, TranslatePipe, LucideBriefcaseBusiness, LucideMapPin, MyJobActions],
  templateUrl: './my-job-row.html',
  styleUrl: './my-job-row.css',
})
export class MyJobRow {
  private readonly i18n = inject(I18nService);

  readonly job = input.required<JobResponse>();

  readonly completed = output<JobActionResult>();
  readonly refreshRequested = output<void>();
  readonly detailsRequested = output<void>();
  readonly reviewRequested = output<void>();

  readonly statusLabel = computed(() => this.i18n.translate(jobStatusLabels[this.job().status]));
  readonly budgetLabel = computed(() =>
    new Intl.NumberFormat(this.i18n.language()).format(this.job().budget),
  );
  readonly dateLabel = computed(() =>
    new Intl.DateTimeFormat(this.i18n.language(), { dateStyle: 'medium' }).format(
      new Date(this.job().createdAt),
    ),
  );
}
