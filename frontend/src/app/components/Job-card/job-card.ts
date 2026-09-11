import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';

import { JobImage, JobListItem } from '../../types/job.js';

@Component({
  selector: 'app-job-card',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './job-card.html',
})
export class JobCard {
  readonly job = input.required<JobListItem>();
  readonly images = input<JobImage[]>([]);
}
