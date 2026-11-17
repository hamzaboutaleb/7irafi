import { TitleCasePipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { LucideBriefcaseBusiness, LucideChevronDown, LucideMapPin } from '@lucide/angular';

import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { JobResponse } from '../../types/job.js';
import { jobStatusLabels } from '../../utils/job.js';

@Component({
  selector: 'app-job-applicants-summary',
  imports: [TitleCasePipe, TranslatePipe, LucideBriefcaseBusiness, LucideChevronDown, LucideMapPin],
  templateUrl: './job-applicants-summary.html',
  styleUrl: './job-applicants-summary.css',
})
export class JobApplicantsSummary {
  private readonly i18n = inject(I18nService);

  readonly job = input.required<JobResponse>();
  readonly budgetLabel = computed(() =>
    new Intl.NumberFormat(this.i18n.language(), {
      maximumFractionDigits: 2,
    }).format(this.job().budget),
  );
  readonly statusLabel = computed(() => this.i18n.translate(jobStatusLabels[this.job().status]));
}
