import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { RatingResponse, RatingService } from '../../services/rating.js';
import { JobResponse } from '../../types/job.js';
import { RatingFormComponent } from '../rating-form/rating-form.js';
import { RatingStars } from '../rating-stars/rating-stars.js';

@Component({
  selector: 'app-job-review-section',
  imports: [TranslatePipe, RatingFormComponent, RatingStars],
  templateUrl: './job-review-section.html',
  styleUrl: './job-review-section.css',
})
export class JobReviewSection {
  private readonly ratings = inject(RatingService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);

  readonly job = input.required<JobResponse>();
  readonly currentUserId = input<string | null>(null);
  readonly ownerName = input('');
  readonly startWithReview = input(false);

  private recipientNameFromReviews = signal('');
  readonly busy = signal(false);
  readonly ratingOpen = signal(false);
  readonly ratingSubmitted = signal(false);
  readonly savedReview = signal<RatingResponse | null>(null);
  readonly reviewState = signal<'loading' | 'loaded' | 'error'>('loading');

  readonly isOwner = computed(() => this.currentUserId() === this.job().clientId);
  readonly recipientName = computed(
    () => this.recipientNameFromReviews() || (!this.isOwner() ? this.ownerName() : ''),
  );
  readonly rateeId = computed(() => {
    const userId = this.currentUserId();
    if (!userId) return null;
    const job = this.job();
    if (job.status !== 'COMPLETED' || !job.assignedWorkerId) return null;
    if (userId === job.clientId) return job.assignedWorkerId;
    if (userId === job.assignedWorkerId) return job.clientId;
    return null;
  });
  readonly hideFooter = computed(
    () => !!this.rateeId() && (this.ratingOpen() || !!this.savedReview()),
  );
  readonly actionDisabled = computed(() => this.busy() || this.reviewState() !== 'loaded');

  constructor() {
    effect(() => {
      if (this.rateeId()) {
        untracked(() => this.loadReview());
      }
    });
  }

  loadReview(): void {
    if (!this.rateeId() || this.busy()) return;
    this.reviewState.set('loading');
    this.ratings
      .getJobReviews(this.job().id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reviews) => {
          const ownReview = reviews.find((review) => review.raterId === this.currentUserId());
          this.savedReview.set(ownReview ?? null);
          this.recipientNameFromReviews.set(
            reviews.find((review) => review.raterId === this.rateeId())?.raterName || '',
          );
          this.ratingOpen.set(!ownReview && this.startWithReview());
          this.reviewState.set('loaded');
        },
        error: () => this.reviewState.set('error'),
      });
  }

  reviewSaved(review: RatingResponse): void {
    this.savedReview.set(review);
    this.ratingSubmitted.set(true);
    this.ratingOpen.set(false);
  }

  openReview(): void {
    if (!this.savedReview() && !this.actionDisabled()) this.ratingOpen.set(true);
  }

  dateLabel(value: string): string {
    return new Intl.DateTimeFormat(this.i18n.language(), { dateStyle: 'medium' }).format(
      new Date(value),
    );
  }
}
