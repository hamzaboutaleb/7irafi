import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Star } from '@lucide/angular';
import { RatingService, RatingResponse, PageResponse } from '../../services/rating.js';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-rating-list',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './rating-list.html',
  styleUrl: './rating-list.css',
})
export class RatingListComponent implements OnInit, OnDestroy {
  @Input() userId!: string;
  @Input() type: 'received' | 'given' = 'received';
  @Input() pageSize = 10;

  ratings: RatingResponse[] = [];
  pagination = { page: 0, size: 10, totalElements: 0, totalPages: 0 };
  loading = false;
  readonly Star = Star;
  private destroy$ = new Subject<void>();

  constructor(private ratingService: RatingService) {}

  ngOnInit(): void {
    this.loadRatings();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadRatings(): void {
    this.loading = true;
    const request$ = this.type === 'received'
      ? this.ratingService.getReceivedByUserId(this.userId, this.pagination.page, this.pagination.size)
      : this.ratingService.getGivenByUserId(this.userId, this.pagination.page, this.pagination.size);

    request$.pipe(takeUntil(this.destroy$)).subscribe({
      next: (response: PageResponse<RatingResponse>) => {
        this.ratings = response.content;
        this.pagination = {
          page: response.page,
          size: response.size,
          totalElements: response.totalElements,
          totalPages: response.totalPages,
        };
        this.loading = false;
      },
      error: () => {
        this.loading = false;
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
}