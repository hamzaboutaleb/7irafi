import { DOCUMENT, TitleCasePipe } from '@angular/common';
import {
  AfterViewInit,
  afterNextRender,
  Component,
  computed,
  effect,
  untracked,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  input,
  OnDestroy,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of, tap } from 'rxjs';
import { ApplicationService } from '../../services/application.js';
import { UserService } from '../../services/user.js';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { JobService } from '../../services/job.js';
import { JobDetail } from '../../types/job.js';
import { PublicUserProfile } from '../../types/user.js';
import { I18nService } from '../../services/i18n.js';
import { TranslationKey } from '../../i18n/language.js';
import { LucideX, LucideMapPin, LucideClock, LucideSun, LucideUserRound, LucideMaximize2, LucideCircleCheck, LucideChevronLeft, LucideChevronRight } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { RatingResponse, RatingService } from '../../services/rating.js';
import { RatingStars } from '../rating-stars/rating-stars.js';
import { RatingFormComponent } from '../rating-form/rating-form.js';

@Component({
  selector: 'app-job-details-modal',
  imports: [TitleCasePipe, RouterLink, TranslatePipe, RatingFormComponent, RatingStars, LucideX, LucideMapPin, LucideClock, LucideSun, LucideUserRound, LucideMaximize2, LucideCircleCheck, LucideChevronLeft, LucideChevronRight],
  templateUrl: './job-details-modal.html',
  styleUrl: './job-details-modal.css',
})
export class JobDetailsModal implements OnInit, AfterViewInit, OnDestroy {
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly imageDialog = viewChild.required<ElementRef<HTMLDialogElement>>('imageDialog');
  private readonly applications = inject(ApplicationService);
  private readonly users = inject(UserService);
  private readonly i18n = inject(I18nService);
  private readonly injector = inject(Injector);
  private readonly reviewSection = viewChild<ElementRef<HTMLElement>>('reviewSection');
  readonly owner = signal<PublicUserProfile | null>(null);
  readonly ownerState = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly ownerImageFailed = signal(false);
  readonly ownerName = computed(() => {
    const owner = this.owner();
    return owner ? `${owner.firstName} ${owner.lastName}`.trim() : this.i18n.translate('chat.jobOwner');
  });
  readonly currentUserId = signal<string | null>(null);
  readonly eligibility = signal<'loading' | 'eligible' | 'ineligible' | 'error'>('loading');
  readonly applying = signal(false);
  readonly applied = signal(false);
  readonly applyError = signal<string | null>(null);
  readonly isOwner = computed(() => this.currentUserId() === this.job()?.clientId);
  readonly ratingOpen = signal(false);
  readonly ratingSubmitted = signal(false);
  readonly savedReview = signal<RatingResponse | null>(null);
  readonly reviewState = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly ratingBusy = signal(false);
  readonly busy = computed(() => this.ratingBusy() || this.applying());
  readonly recipientName = signal('');
  private readonly ratings = inject(RatingService);
  readonly rateeId = computed(() => {
    const job = this.job();
    const userId = this.currentUserId();
    if (!job || !userId || job.status !== 'COMPLETED' || !job.assignedWorkerId) return null;
    if (userId === job.clientId) return job.assignedWorkerId;
    if (userId === job.assignedWorkerId) return job.clientId;
    return null;
  });
  readonly canApply = computed(() => this.eligibility() === 'eligible' &&
    !this.isOwner() && this.job()?.status === 'OPEN' && !this.applying() && !this.applied());
  private previousOverflow = '';

  readonly jobId = input.required<string>();
  readonly alreadyApplied = input(false);
  readonly startWithReview = input(false);
  readonly closed = output<void>();
  readonly job = signal<JobDetail | null>(null);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedImage = signal(0);
  private readonly failedImages = signal<ReadonlySet<string>>(new Set());
  readonly images = computed(() => (this.job()?.images ?? []).filter(image => !this.failedImages().has(image.id)));
  readonly imageUrl = computed(() =>
    this.images()[this.selectedImage()]?.url ?? null,
  );
  readonly budgetLabel = computed(() => new Intl.NumberFormat(this.i18n.language(), { maximumFractionDigits: 2 }).format(this.job()?.budget ?? 0));
  readonly statusLabel = computed(() => {
    const keys = { OPEN: 'dashboard.status.OPEN', ASSIGNED: 'dashboard.status.ASSIGNED', COMPLETED: 'dashboard.status.COMPLETED', CANCELLED: 'dashboard.status.CANCELLED' } as const;
    return this.i18n.translate(keys[this.job()?.status ?? 'OPEN']);
  });
  readonly durationLabel = computed(() => {
    const keys = { HALF_DAY: 'createJob.halfDay', FULL_DAY: 'createJob.fullDay', MULTI_DAY: 'createJob.multipleDays' } as const;
    return this.i18n.translate(keys[this.job()?.estimatedDuration ?? 'HALF_DAY']);
  });
  readonly timeLabel = computed(() => {
    const keys = { MORNING: 'createJob.morning', AFTERNOON: 'createJob.afternoon', EVENING: 'createJob.evening', ANYTIME: 'createJob.anytime' } as const;
    return this.i18n.translate(keys[this.job()?.preferredTime ?? 'ANYTIME']);
  });

  dateLabel(value: string): string {
    return new Intl.DateTimeFormat(this.i18n.language(), { dateStyle: 'medium' }).format(new Date(value));
  }

  constructor() {
    // Identity and job details arrive independently. Check once both identify the
    // other participant; never offer an empty form before checking saved reviews.
    effect(() => {
      if (this.rateeId()) untracked(() => this.loadReview());
    });
  }

  loadReview(): void {
    if (!this.rateeId() || this.ratingBusy()) return;
    this.reviewState.set('loading');
    this.ratings.getByJobId(this.jobId()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: reviews => {
        const ownReview = reviews.find(review => review.raterId === this.currentUserId());
        this.savedReview.set(ownReview ?? null);
        this.recipientName.set(ownReview?.rateeName || reviews.find(review => review.raterId === this.rateeId())?.raterName || '');
        this.ratingOpen.set(!ownReview && this.startWithReview());
        this.reviewState.set('loaded');
        if (this.startWithReview()) this.focusReview();
      },
      error: () => this.reviewState.set('error'),
    });
  }

  reviewSaved(review: RatingResponse): void {
    this.savedReview.set(review);
    this.ratingSubmitted.set(true);
    this.ratingOpen.set(false);
  }

  protectSubmission(event: Event): void {
    if (this.busy()) event.preventDefault();
  }

  focusReview(openForm = false): void {
    if (openForm && !this.savedReview() && this.reviewState() === 'loaded') this.ratingOpen.set(true);
    afterNextRender(() => {
      const section = this.reviewSection()?.nativeElement;
      section?.scrollIntoView({ block: 'start' });
      section?.querySelector<HTMLElement>('h3')?.focus({ preventScroll: true });
    }, { injector: this.injector });
  }

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
    this.imageDialog().nativeElement.close();
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
        this.failedImages.set(new Set());
        this.loadOwner();
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set(this.i18n.translate('jobDetails.loadFailed'));
        this.isLoading.set(false);
      },
    });
  }

  loadOwner(): void {
    const userId = this.job()?.clientId;
    if (!userId) return;
    this.ownerState.set('loading');
    this.owner.set(null);
    this.ownerImageFailed.set(false);
    this.users.getPublicUserProfile(userId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => { this.owner.set(response.data); this.ownerState.set('loaded'); },
      error: () => this.ownerState.set('error'),
    });
  }

  loadEligibility(): void {
    this.eligibility.set('loading');
    forkJoin({
      user: this.users.getCurrentUser().pipe(tap(user => this.currentUserId.set(user.data.id))),
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
        const messages: Record<string, TranslationKey> = {
          WORKER_REQUIRED: 'jobDetails.needsProfile', OWN_JOB_APPLICATION: 'jobDetails.ownJob',
          JOB_NOT_OPEN: 'jobDetails.closed', JOB_NOT_FOUND: 'jobDetails.noLongerAvailable',
          JOB_ACCESS_DENIED: 'jobDetails.accessDenied', APPLICATION_NOT_FOUND: 'jobDetails.applicationMissing',
        };
        this.applyError.set(this.i18n.translate(error.status === 0 || error.status === 503
          ? 'jobDetails.serviceUnavailable' : messages[error.error?.code] ?? 'jobDetails.applyFailed'));
        if (error.status === 409 || error.status === 404) this.loadDetails();
      },
    });
  }

  openImage(): void {
    if (!this.imageUrl()) return;
    this.imageDialog().nativeElement.showModal();
  }

  changeImage(direction: number): void {
    const count = this.images().length;
    if (count < 2) return;
    this.selectedImage.update((index) => (index + direction + count) % count);
  }

  onImageError(event: Event): void {
    const source = (event.target as HTMLImageElement).getAttribute('src');
    const failed = (this.job()?.images ?? []).filter(image => image.url === source).map(image => image.id);
    this.failedImages.update(current => new Set([...current, ...failed]));
    this.selectedImage.set(0);
    if (!this.images().length) this.imageDialog().nativeElement.close();
  }
}
