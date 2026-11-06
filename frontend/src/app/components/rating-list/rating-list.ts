import { ChangeDetectorRef, Component, EventEmitter, inject, Input, Output, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RatingStars } from '../rating-stars/rating-stars.js';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { RatingService, RatingResponse, PageResponse } from '../../services/rating.js';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-rating-list',
  standalone: true,
  imports: [CommonModule, RatingStars, TranslatePipe, RouterLink],
  templateUrl: './rating-list.html',
  styleUrl: './rating-list.css',
})
export class RatingListComponent implements OnChanges, OnDestroy, OnInit {
  @Input() userId!: string;
  @Input() type: 'received' | 'given' = 'received';
  @Input() pageSize = 10;
  @Output() stateChange = new EventEmitter<'loading' | 'loaded' | 'error'>();
  @Output() retryRequested = new EventEmitter<void>();
  @Output() viewJob = new EventEmitter<string>();

  ratings: RatingResponse[] = [];
  pagination = { page: 0, size: 10, totalElements: 0, totalPages: 0 };
  loading = false;
  error: string | null = null;
  private destroy$ = new Subject<void>();
  private cancelRequest$ = new Subject<void>();
  private readonly changeDetector = inject(ChangeDetectorRef);

  constructor(private ratingService: RatingService) {}

  ngOnInit(): void {
    // A review submitted in the job dialog should appear without refreshing the page.
    this.ratingService.reviewsChanged.pipe(takeUntil(this.destroy$)).subscribe(() => this.loadRatings(0));
  }

  ngOnChanges(): void {
    this.ratings = [];
    this.pagination = { page: 0, size: this.pageSize, totalElements: 0, totalPages: 0 };
    this.loadRatings();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.cancelRequest$.complete();
  }

  loadRatings(page = this.pagination.page): void {
    this.cancelRequest$.next();
    if (!this.userId) return;
    this.loading = true;
    this.stateChange.emit('loading');
    this.error = null;
    const request$ = this.type === 'received'
      ? this.ratingService.getReceivedByUserId(this.userId, page, this.pagination.size)
      : this.ratingService.getGivenByUserId(this.userId, page, this.pagination.size);

    request$.pipe(takeUntil(this.destroy$), takeUntil(this.cancelRequest$)).subscribe({
      next: (response: PageResponse<RatingResponse>) => {
        this.ratings = response.content;
        this.pagination = {
          page: response.page,
          size: response.size,
          totalElements: response.totalElements,
          totalPages: response.totalPages,
        };
        this.loading = false;
        this.stateChange.emit('loaded');
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.error = 'reviews.loadError';
        this.stateChange.emit('error');
        this.loading = false;
        this.changeDetector.markForCheck();
      },
    });
  }

  onPageChange(page: number): void {
    if (!this.loading && page >= 0 && page < this.pagination.totalPages && page !== this.pagination.page) {
      this.loadRatings(page);
    }
  }

  goToPrevious(): void {
    this.onPageChange(this.pagination.page - 1);
  }

  goToNext(): void {
    this.onPageChange(this.pagination.page + 1);
  }

  retry(): void {
    this.retryRequested.emit();
    this.loadRatings();
  }
}
