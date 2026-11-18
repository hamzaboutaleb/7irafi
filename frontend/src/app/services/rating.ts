import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { map, Observable, Subject, tap } from 'rxjs';

import { ApiSuccess, Page } from '../types/api.js';
import { AuthService } from './auth.js';

export interface CreateRatingRequest {
  jobId: string;
  rateeId: string;
  score: number;
  comment?: string;
}

export interface RatingResponse {
  id: string;
  jobId: string;
  raterId: string;
  rateeId: string;
  score: number;
  comment: string | null;
  createdAt: string;
  raterName: string | null;
  rateeName?: string | null;
  jobTitle?: string | null;
}

export interface UserRatingSummary {
  userId: string;
  averageScore: number;
  totalRatings: number;
  scoreDistribution: Record<number, number>;
}

@Injectable({
  providedIn: 'root',
})
export class RatingService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly ratingsEndpoint = 'ratings';

  // Include the reviewer ID so different users never share a reviewed-job flag.
  private readonly reviewedJobKeys = signal<ReadonlySet<string>>(new Set());
  private readonly reviewsChangedSubject = new Subject<void>();

  readonly reviewsChanged = this.reviewsChangedSubject.asObservable();

  hasReviewedJob(jobId: string): boolean {
    const currentUserId = this.authService.currentUserId();
    if (!currentUserId) return false;

    const reviewKey = this.createReviewedJobKey(currentUserId, jobId);

    return this.reviewedJobKeys().has(reviewKey);
  }

  publishReview(reviewRequest: CreateRatingRequest): Observable<RatingResponse> {
    return this.http.post<ApiSuccess<RatingResponse>>(this.ratingsEndpoint, reviewRequest).pipe(
      map((response) => response.data),
      tap((publishedReview) => {
        this.rememberReviewedJobs([publishedReview], publishedReview.raterId);
        this.reviewsChangedSubject.next();
      }),
    );
  }

  getJobReviews(jobId: string): Observable<RatingResponse[]> {
    const url = `${this.ratingsEndpoint}/job/${jobId}`;

    return this.http.get<ApiSuccess<RatingResponse[]>>(url).pipe(
      map((response) => response.data),
      tap((reviews) => {
        const currentUserId = this.authService.currentUserId();
        this.rememberReviewedJobs(reviews, currentUserId);
      }),
    );
  }

  getReceivedReviews(
    userId: string,
    pageNumber = 0,
    pageSize = 20,
  ): Observable<Page<RatingResponse>> {
    const url = `${this.ratingsEndpoint}/user/${userId}/received`;
    const params = this.createPaginationParams(pageNumber, pageSize);

    return this.http
      .get<ApiSuccess<Page<RatingResponse>>>(url, { params })
      .pipe(map((response) => response.data));
  }

  getWrittenReviews(
    userId: string,
    pageNumber = 0,
    pageSize = 20,
  ): Observable<Page<RatingResponse>> {
    const url = `${this.ratingsEndpoint}/user/${userId}/given`;
    const params = this.createPaginationParams(pageNumber, pageSize);

    return this.http.get<ApiSuccess<Page<RatingResponse>>>(url, { params }).pipe(
      map((response) => response.data),
      tap((reviewPage) => this.rememberReviewedJobs(reviewPage.content, userId)),
    );
  }

  getUserRatingSummary(userId: string): Observable<UserRatingSummary> {
    const url = `${this.ratingsEndpoint}/user/${userId}/summary`;

    return this.http.get<ApiSuccess<UserRatingSummary>>(url).pipe(map((response) => response.data));
  }

  private createPaginationParams(pageNumber: number, pageSize: number): HttpParams {
    return new HttpParams().set('page', pageNumber).set('size', pageSize);
  }

  private createReviewedJobKey(userId: string, jobId: string): string {
    return `${userId}:${jobId}`;
  }

  private rememberReviewedJobs(reviews: RatingResponse[], reviewerId: string | null): void {
    if (!reviewerId || reviews.length === 0) return;

    const writtenReviews = reviews.filter((review) => review.raterId === reviewerId);
    if (writtenReviews.length === 0) return;

    const newReviewKeys = writtenReviews.map((review) =>
      this.createReviewedJobKey(reviewerId, review.jobId),
    );

    this.reviewedJobKeys.update((existingKeys) => {
      const updatedKeys = new Set(existingKeys);

      for (const reviewKey of newReviewKeys) {
        updatedKeys.add(reviewKey);
      }

      return updatedKeys;
    });
  }
}
