import { DatePipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  inject,
  input,
  OnChanges,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, Subscription } from 'rxjs';

import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { RatingResponse, RatingService } from '../../services/rating.js';
import { Page } from '../../types/api.js';
import { RatingStars } from '../rating-stars/rating-stars.js';

type ReviewType = 'received' | 'written';
type ReviewLoadState = 'loading' | 'loaded' | 'error';

/**
 * Displays one page of a user's received or written reviews.
 * Input changes start at the first page. Publishing a review refreshes the list.
 * Existing reviews stay visible during pagination, retry, and refresh requests.
 */
@Component({
  selector: 'app-rating-list',
  imports: [DatePipe, RouterLink, RatingStars, TranslatePipe],
  templateUrl: './rating-list.html',
  styleUrl: './rating-list.css',
})
export class RatingListComponent implements OnChanges, OnInit {
  readonly userId = input.required<string>();
  readonly reviewType = input<ReviewType>('received');
  readonly pageSize = input(10);

  readonly loadStateChange = output<ReviewLoadState>();
  readonly retryRequested = output<void>();
  readonly viewJobRequested = output<string>();

  private readonly ratingService = inject(RatingService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);

  // Keep reviews and pagination from the same response together.
  readonly reviewPage = signal<Page<RatingResponse>>(this.createEmptyPage());
  private readonly loadState = signal<ReviewLoadState>('loaded');
  private reviewRequest?: Subscription;

  ngOnChanges(): void {
    this.reviewPage.set(this.createEmptyPage());
    this.loadReviews(0);
  }

  ngOnInit(): void {
    this.ratingService.reviewsChanged
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadReviews(0));
  }

  reviews(): RatingResponse[] {
    return this.reviewPage().content;
  }

  isLoading(): boolean {
    return this.loadState() === 'loading';
  }

  hasLoadError(): boolean {
    return this.loadState() === 'error';
  }

  personLabel(review: RatingResponse): string {
    const unknownName = this.i18n.translate('reviews.unknownUser');

    if (this.reviewType() === 'received') {
      return review.raterName || unknownName;
    }

    const reviewedUserName = review.rateeName || unknownName;

    return this.i18n.translate('reviews.for', { name: reviewedUserName });
  }

  personInitial(review: RatingResponse): string {
    const personName = this.reviewType() === 'received' ? review.raterName : review.rateeName;

    return (personName || '?').slice(0, 1).toUpperCase();
  }

  /** A newer request replaces the old one; late responses cannot overwrite this page. */
  loadReviews(pageNumber = this.reviewPage().page): void {
    this.reviewRequest?.unsubscribe();

    const userId = this.userId();
    if (!userId) {
      this.setLoadState('loaded');
      return;
    }

    this.setLoadState('loading');

    this.reviewRequest = this.requestReviewPage(userId, pageNumber)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reviewPage) => {
          this.reviewPage.set(reviewPage);
          this.setLoadState('loaded');
        },
        error: () => this.setLoadState('error'),
      });
  }

  goToPage(pageNumber: number): void {
    if (this.isLoading()) return;

    const { page, totalPages } = this.reviewPage();
    if (pageNumber < 0 || pageNumber >= totalPages || pageNumber === page) return;

    this.loadReviews(pageNumber);
  }

  retryLoadingReviews(): void {
    this.retryRequested.emit();
    this.loadReviews();
  }

  private createEmptyPage(): Page<RatingResponse> {
    return {
      content: [],
      page: 0,
      size: this.pageSize(),
      totalElements: 0,
      totalPages: 0,
    };
  }

  private requestReviewPage(userId: string, pageNumber: number): Observable<Page<RatingResponse>> {
    const pageSize = this.reviewPage().size;

    if (this.reviewType() === 'received') {
      return this.ratingService.getReceivedReviews(userId, pageNumber, pageSize);
    }

    return this.ratingService.getWrittenReviews(userId, pageNumber, pageSize);
  }

  private setLoadState(state: ReviewLoadState): void {
    this.loadState.set(state);
    this.loadStateChange.emit(state);
  }
}
