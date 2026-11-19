import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideBriefcaseBusiness } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { JobActionResult, JobResponse } from '../../types/job.js';
import { MyJobRow } from '../my-job-row/my-job-row.js';
import { MyJobsLoading } from '../my-jobs-loading/my-jobs-loading.js';

@Component({
  selector: 'app-my-jobs-results',
  imports: [RouterLink, LucideBriefcaseBusiness, TranslatePipe, MyJobRow, MyJobsLoading],
  templateUrl: './my-jobs-results.html',
  styleUrl: './my-jobs-results.css',
})
export class MyJobsResults {
  readonly jobs = input.required<readonly JobResponse[]>();
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly hasJobs = input(false);

  readonly reloadRequested = output<void>();
  readonly showAllRequested = output<void>();
  readonly completed = output<JobActionResult>();
  readonly detailsRequested = output<string>();
  readonly reviewRequested = output<string>();
}
