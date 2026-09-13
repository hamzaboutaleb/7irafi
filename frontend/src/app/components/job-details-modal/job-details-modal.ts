import { DatePipe, DecimalPipe, DOCUMENT, TitleCasePipe } from '@angular/common';
import {
  AfterViewInit,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  OnDestroy,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { JobService } from '../../services/job.js';
import { EstimatedDuration, JobDetail } from '../../types/job.js';

@Component({
  selector: 'app-job-details-modal',
  imports: [DatePipe, DecimalPipe, TitleCasePipe],
  templateUrl: './job-details-modal.html',
})
export class JobDetailsModal implements OnInit, AfterViewInit, OnDestroy {
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private previousOverflow = '';

  readonly jobId = input.required<string>();
  readonly closed = output<void>();
  readonly job = signal<JobDetail | null>(null);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedImage = signal(0);
  readonly placeholderUrl = 'job-placeholder.png';
  readonly imageUrl = computed(() =>
    this.job()?.images[this.selectedImage()]?.url || this.placeholderUrl,
  );
  readonly durationLabels: Record<EstimatedDuration, string> = {
    HALF_DAY: 'Half day',
    FULL_DAY: 'Full day',
    MULTI_DAY: 'Multiple days',
  };

  ngOnInit(): void {
    this.loadDetails();
  }

  ngAfterViewInit(): void {
    this.previousOverflow = this.document.body.style.overflow;
    this.document.body.style.overflow = 'hidden';
    this.dialog().nativeElement.showModal();
  }

  ngOnDestroy(): void {
    this.dialog().nativeElement.close();
    this.document.body.style.overflow = this.previousOverflow;
  }

  loadDetails(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.jobService.getJob(this.jobId()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (job) => {
        this.job.set(job);
        this.selectedImage.set(0);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Unable to load this job. Please try again.');
        this.isLoading.set(false);
      },
    });
  }

  onImageError(event: Event): void {
    const image = event.target as HTMLImageElement;
    if (image.getAttribute('src') !== this.placeholderUrl) {
      image.src = this.placeholderUrl;
    }
  }
}
