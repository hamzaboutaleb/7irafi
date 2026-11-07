import { TitleCasePipe } from '@angular/common';
import { Component, computed, inject, input, linkedSignal, output } from '@angular/core';

import { JobListItem } from '../../types/job.js';
import { ApplicationStatus } from '../../types/application.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { LucideDynamicIcon, LucideMapPin, LucideBriefcaseBusiness, LucideWrench, LucideHammer, LucideZap, LucidePaintBucket, LucideTreePine, LucideHouse } from '@lucide/angular';

@Component({
  selector: 'app-job-card',
  imports: [TitleCasePipe, TranslatePipe, LucideDynamicIcon, LucideMapPin],
  templateUrl: './job-card.html',
  styleUrl: './job-card.css',
})
export class JobCard {
  private readonly i18n = inject(I18nService);

  readonly job = input.required<JobListItem>();
  readonly applicationStatus = input<ApplicationStatus | null>(null);
  readonly appliedAt = input<string | null>(null);
  readonly statusLabel = computed(() => {
    const labels = {
      OPEN: 'dashboard.status.OPEN', ASSIGNED: 'dashboard.status.ASSIGNED', COMPLETED: 'dashboard.status.COMPLETED', CANCELLED: 'dashboard.status.CANCELLED',
      PENDING: 'dashboard.status.PENDING', ACCEPTED: 'dashboard.status.ACCEPTED', NOT_SELECTED: 'dashboard.status.NOT_SELECTED', CLOSED: 'dashboard.status.CLOSED',
    } as const;
    const status = this.job().status === 'COMPLETED' ? 'COMPLETED' : this.applicationStatus() ?? this.job().status;
    return this.i18n.translate(labels[status]);
  });
  readonly canReview = input(false);
  readonly reviewed = input(false);
  readonly reviewRequested = output<void>();
  readonly viewDetails = output<void>();

  readonly tradeIcon = computed(() => {
    const icons = {
      PLUMBING: LucideWrench, CARPENTRY: LucideHammer, ELECTRICAL: LucideZap,
      PAINTING: LucidePaintBucket, GARDENING: LucideTreePine, CLEANING: LucideHouse,
    };
    const skill = this.job().skill.toUpperCase().replaceAll(' ', '_');
    return Object.entries(icons).find(([key]) => key === skill)?.[1] ?? LucideBriefcaseBusiness;
  });
  readonly budgetLabel = computed(() => new Intl.NumberFormat(this.i18n.language(), { maximumFractionDigits: 2 }).format(this.job().budget));
  readonly dateLabel = computed(() => new Intl.DateTimeFormat(this.i18n.language(), { dateStyle: 'medium' }).format(new Date(this.appliedAt() || this.job().createdAt)));

  private readonly thumbnail = computed(() => ({
    jobId: this.job().id,
    url: this.job().thumbnailUrl,
  }));

  readonly imageFailed = linkedSignal({
    source: this.thumbnail,
    computation: () => false,
  });

  readonly imageUrl = computed(() =>
    this.imageFailed() ? null : this.job().thumbnailUrl,
  );

  onImageError(): void {
    this.imageFailed.set(true);
  }
}
