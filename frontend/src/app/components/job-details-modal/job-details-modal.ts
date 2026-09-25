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
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { ApplicationService, applicationError } from '../../services/application.js';
import { UserService } from '../../services/user.js';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { JobService } from '../../services/job.js';
import { EstimatedDuration, JobDetail } from '../../types/job.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-job-details-modal',
  imports: [DatePipe, DecimalPipe, TitleCasePipe, RouterLink, TranslatePipe],
  templateUrl: './job-details-modal.html',
})
export class JobDetailsModal implements OnInit, AfterViewInit, OnDestroy {
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly applications = inject(ApplicationService);
  private readonly users = inject(UserService);
  readonly currentUserId = signal<string | null>(null);
  readonly eligibility = signal<'loading' | 'eligible' | 'ineligible' | 'error'>('loading');
  readonly applying = signal(false);
  readonly applied = signal(false);
  readonly applyError = signal<string | null>(null);
  readonly isOwner = computed(() => this.currentUserId() === this.job()?.clientId);
  readonly canApply = computed(() => this.eligibility() === 'eligible' &&
    !this.isOwner() && this.job()?.status === 'OPEN' && !this.applying() && !this.applied());
  private previousOverflow = '';

  readonly jobId = input.required<string>();
  readonly alreadyApplied = input(false);
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
    this.applied.set(this.alreadyApplied());
    this.loadDetails();
    this.loadEligibility();
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

  loadEligibility(): void {
    this.eligibility.set('loading');
    forkJoin({
      user: this.users.getCurrentUser(),
      professional: this.users.getCurrentProfessionalProfile().pipe(
        catchError((error: HttpErrorResponse) => {
          if (error.status === 404) return of(null);
          throw error;
        }),
      ),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ user, professional }) => {
        this.currentUserId.set(user.data.id);
        this.eligibility.set(professional?.data.onboardingStatus === 'ACTIVE' ? 'eligible' : 'ineligible');
      },
      error: () => this.eligibility.set('error'),
    });
  }

  apply(): void {
    if (!this.canApply()) return;
    this.applying.set(true);
    this.applyError.set(null);
    this.applications.apply(this.jobId()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.applied.set(true);
        this.applying.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.applying.set(false);
        if (error.error?.code === 'DUPLICATE_APPLICATION') {
          this.applied.set(true);
          return;
        }
        if (error.error?.code === 'WORKER_REQUIRED') this.eligibility.set('ineligible');
        this.applyError.set(applicationError(error, 'Unable to apply. Please try again.'));
        if (error.status === 409 || error.status === 404) this.loadDetails();
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
