import { DOCUMENT, TitleCasePipe } from '@angular/common';
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
import { UserService } from '../../services/user.js';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { JobService } from '../../services/job.js';
import { JobDetail } from '../../types/job.js';
import { I18nService } from '../../services/i18n.js';
import { LucideX, LucideMapPin, LucideClock, LucideSun, LucideChevronRight } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { JobGallery } from '../job-gallery/job-gallery.js';
import { JobReviewSection } from '../job-review-section/job-review-section.js';
import { JobOwnerCard } from '../job-owner-card/job-owner-card.js';
import { JobApplicationAction } from '../job-application-action/job-application-action.js';
import { jobDurationLabels, jobStatusLabels, jobTimeLabels } from '../../utils/job.js';

@Component({
  selector: 'app-job-details-modal',
  imports: [
    TitleCasePipe,
    TranslatePipe,
    JobGallery,
    JobReviewSection,
    JobOwnerCard,
    JobApplicationAction,
    LucideX,
    LucideMapPin,
    LucideClock,
    LucideSun,
    LucideChevronRight,
  ],
  templateUrl: './job-details-modal.html',
  styleUrls: ['./job-details-modal.css', './job-details-content.css'],
})
export class JobDetailsModal implements OnInit, AfterViewInit, OnDestroy {
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly users = inject(UserService);
  private readonly i18n = inject(I18nService);

  readonly reviewSection = viewChild(JobReviewSection);
  readonly ownerCard = viewChild(JobOwnerCard);
  readonly applicationAction = viewChild(JobApplicationAction);

  readonly currentUserId = signal<string | null>(null);
  readonly identityFailed = signal(false);

  readonly busy = computed(
    () => !!this.applicationAction()?.busy() || !!this.reviewSection()?.busy(),
  );
  private previousOverflow = '';

  readonly jobId = input.required<string>();
  readonly alreadyApplied = input(false);
  readonly startWithReview = input(false);
  readonly closed = output<void>();
  readonly job = signal<JobDetail | null>(null);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly budgetLabel = computed(() =>
    new Intl.NumberFormat(this.i18n.language(), { maximumFractionDigits: 2 }).format(
      this.job()?.budget ?? 0,
    ),
  );
  readonly statusLabel = computed(() =>
    this.i18n.translate(jobStatusLabels[this.job()?.status ?? 'OPEN']),
  );
  readonly durationLabel = computed(() =>
    this.i18n.translate(jobDurationLabels[this.job()?.estimatedDuration ?? 'HALF_DAY']),
  );
  readonly timeLabel = computed(() =>
    this.i18n.translate(jobTimeLabels[this.job()?.preferredTime ?? 'ANYTIME']),
  );

  protectSubmission(event: Event): void {
    if (this.busy()) event.preventDefault();
  }

  ngOnInit(): void {
    this.loadDetails();
    this.loadCurrentUser();
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
    this.jobService
      .getJob(this.jobId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (job) => {
          this.job.set(job);
          this.isLoading.set(false);
        },
        error: () => {
          this.errorMessage.set(this.i18n.translate('jobDetails.loadFailed'));
          this.isLoading.set(false);
        },
      });
  }

  loadCurrentUser(): void {
    this.identityFailed.set(false);
    this.users
      .getCurrentUser()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (user) => this.currentUserId.set(user.data.id),
        error: () => this.identityFailed.set(true),
      });
  }
}
