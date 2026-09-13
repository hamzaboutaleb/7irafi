import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Footer } from '../../../components/footer/footer.js';
import { JobCard } from '../../../components/Job-card/job-card.js';
import { JobDetailsModal } from '../../../components/job-details-modal/job-details-modal.js';
import { Navbar } from '../../../components/navbar/navbar.js';
import { JobService } from '../../../services/job.js';
import { JobListItem, JobResponse, UpdateJobRequest } from '../../../types/job.js';

type MyJob = JobListItem & Pick<JobResponse, 'addressLine'>;

@Component({
  selector: 'app-my-jobs',
  imports: [Navbar, Footer, RouterLink, ReactiveFormsModule, JobCard, JobDetailsModal],
  templateUrl: './my-jobs.html',
})
export class MyJobs implements OnInit {
  private readonly jobService = inject(JobService);
  private readonly formBuilder = inject(FormBuilder);

  readonly jobs = signal<MyJob[]>([]);
  readonly selectedJobId = signal<string | null>(null);
  readonly editingJob = signal<JobListItem | null>(null);
  readonly isLoading = signal(true);
  readonly busyJobId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly editForm = this.formBuilder.nonNullable.group({
    title: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
    description: ['', [Validators.required, Validators.pattern(/\S/)]],
    addressLine: ['', Validators.maxLength(200)],
  });

  ngOnInit(): void {
    this.loadJobs();
  }

  loadJobs(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.jobService.getMyJobs().subscribe({
      next: (jobs) => {
        this.jobs.set(jobs.map((job) => this.toListItem(job)));
        this.isLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(this.messageFor(error, 'Unable to load your jobs.'));
        this.isLoading.set(false);
      },
    });
  }

  openEdit(job: MyJob): void {
    this.successMessage.set(null);
    this.errorMessage.set(null);
    this.editingJob.set(job);
    this.editForm.setValue({
      title: job.title,
      description: job.description,
      addressLine: job.addressLine ?? '',
    });
  }

  closeEdit(): void {
    if (!this.busyJobId()) this.editingJob.set(null);
  }

  saveEdit(): void {
    const job = this.editingJob();
    if (!job || this.editForm.invalid || this.busyJobId()) {
      this.editForm.markAllAsTouched();
      return;
    }

    const value = this.editForm.getRawValue();
    const request: UpdateJobRequest = {
      title: value.title.trim(),
      description: value.description.trim(),
      addressLine: value.addressLine.trim() || null,
    };
    this.busyJobId.set(job.id);
    this.errorMessage.set(null);
    this.jobService.updateJob(job.id, request).subscribe({
      next: (updated) => {
        this.jobs.update((jobs) => jobs.map((item) => item.id === job.id ? this.toListItem(updated) : item));
        this.editingJob.set(null);
        this.busyJobId.set(null);
        this.successMessage.set('Job updated successfully.');
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(this.messageFor(error, 'Unable to update this job.'));
        this.busyJobId.set(null);
      },
    });
  }

  cancelJob(job: MyJob): void {
    if (this.busyJobId() || !window.confirm(`Cancel “${job.title}”?`)) return;
    this.mutate(job.id, () => this.jobService.cancelJob(job.id), 'Job cancelled successfully.');
  }

  deleteJob(job: MyJob): void {
    if (this.busyJobId() || !window.confirm(`Delete “${job.title}”? This cannot be undone.`)) return;
    this.busyJobId.set(job.id);
    this.errorMessage.set(null);
    this.jobService.deleteJob(job.id).subscribe({
      next: () => {
        this.jobs.update((jobs) => jobs.filter((item) => item.id !== job.id));
        this.busyJobId.set(null);
        this.successMessage.set('Job deleted successfully.');
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(this.messageFor(error, 'Unable to delete this job.'));
        this.busyJobId.set(null);
      },
    });
  }

  private mutate(id: string, request: () => ReturnType<JobService['cancelJob']>, success: string): void {
    this.busyJobId.set(id);
    this.errorMessage.set(null);
    request().subscribe({
      next: (updated) => {
        this.jobs.update((jobs) => jobs.map((job) => job.id === id ? this.toListItem(updated) : job));
        this.busyJobId.set(null);
        this.successMessage.set(success);
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(this.messageFor(error, 'Unable to change this job.'));
        this.busyJobId.set(null);
      },
    });
  }

  private toListItem(job: JobResponse): MyJob {
    return { ...job, thumbnailUrl: null };
  }

  private messageFor(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 0) return 'The server cannot be reached.';
    if (error.status === 401) return 'Your session has expired. Please sign in again.';
    if (error.status === 403) return 'You are not allowed to manage this job.';
    return error.error?.message ?? error.error?.error?.message ?? fallback;
  }
}
