import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { Component, computed, input, linkedSignal, output } from '@angular/core';

import { JobListItem } from '../../types/job.js';

@Component({
  selector: 'app-job-card',
  imports: [DatePipe, DecimalPipe, TitleCasePipe],
  templateUrl: './job-card.html',
})
export class JobCard {

  readonly job = input.required<JobListItem>();
  readonly viewDetails = output<void>();;

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
