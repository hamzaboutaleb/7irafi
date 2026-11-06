import { ChangeDetectorRef, Component, inject, Input, OnChanges, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideStar } from '@lucide/angular';
import { RatingService, RatingResponse, PageResponse } from '../../services/rating.js';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-rating-list',
  standalone: true,
  imports: [CommonModule, LucideStar],
  templateUrl: './rating-list.html',
  styleUrl: './rating-list.css',
})
export class RatingListComponent implements OnChanges, OnDestroy {
  @Input() userId!: string;
  @Input() type: 'received' | 'given' = 'received';
  @Input() pageSize = 10;

  ratings: RatingResponse[] = [];
  pagination = { page: 0, size: 10, totalElements: 0, totalPages: 0 };
  loading = false;
  error: string | null = null;
  private destroy$ = new Subject<void>();
  private cancelRequest$ = new Subject<void>();
  private readonly changeDetector = inject(ChangeDetectorRef);

  constructor(private ratingService: RatingService) {}

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

  loadRatings(): void {
    this.cancelRequest$.next();
    if (!this.userId) return;
    this.loading = true;
    this.error = null;
    const request$ = this.type === 'received'
      ? this.ratingService.getReceivedByUserId(this.userId, this.pagination.page, this.pagination.size)
      : this.ratingService.getGivenByUserId(this.userId, this.pagination.page, this.pagination.size);

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
        this.changeDetector.markForCheck();
      },
      error: (err) => {
        this.error = 'Failed to load ratings. Please try again.';
        this.loading = false;
        this.changeDetector.markForCheck();
      },
    });
  }

  onPageChange(page: number): void {
    if (page >= 0 && page < this.pagination.totalPages && page !== this.pagination.page) {
      this.pagination.page = page;
      this.loadRatings();
    }
  }

  goToPrevious(): void {
    this.onPageChange(this.pagination.page - 1);
  }

  goToNext(): void {
    this.onPageChange(this.pagination.page + 1);
  }

  retry(): void {
    this.loadRatings();
  }
}
