import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { Component, computed, input, linkedSignal, output } from '@angular/core';

import { JobListItem } from '../../types/job.js';
import { ApplicationStatus, applicationStatusLabels } from '../../types/application.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-job-card',
  imports: [DatePipe, DecimalPipe, TitleCasePipe, TranslatePipe],
  templateUrl: './job-card.html',
})
export class JobCard {

  readonly job = input.required<JobListItem>();
  readonly applicationStatus = input<ApplicationStatus | null>(null);
  readonly appliedAt = input<string | null>(null);
  readonly applicationLabel = computed(() => {
    const status = this.applicationStatus();
    return status ? applicationStatusLabels[status] : null;
  });
  readonly canReview = input(false);
  readonly reviewed = input(false);
  readonly reviewRequested = output<void>();
  readonly viewDetails = output<void>();

  readonly placeholderUrl = 'job-placeholder.png';

  private readonly thumbnail = computed(() => ({
    jobId: this.job().id,
    url: this.job().thumbnailUrl,
  }));

  readonly imageFailed = linkedSignal({
    source: this.thumbnail,
    computation: () => false,
  });

  readonly imageUrl = computed(() =>
    this.imageFailed() ? this.placeholderUrl : this.job().thumbnailUrl || this.placeholderUrl,
  );

  onImageError(): void {
    if (this.imageUrl() !== this.placeholderUrl) {
      this.imageFailed.set(true);
    }
  }
}
