import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { AuthService } from './auth.js';
import { map, Observable, Subject, tap } from 'rxjs';

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

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
}

@Injectable({
  providedIn: 'root',
})
export class RatingService {
  private http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private baseUrl = 'ratings';
  private readonly reviewsChangedSubject = new Subject<void>();
  readonly reviewsChanged = this.reviewsChangedSubject.asObservable();
  private readonly reviewedJobs = signal(new Set<string>());

  hasReviewed(jobId: string): boolean {
    return this.reviewedJobs().has(`${this.auth.currentUserId()}:${jobId}`);
  }

  private rememberReviews(reviews: RatingResponse[], userId: string): void {
    this.reviewedJobs.update(previous => new Set([
      ...previous, ...reviews.filter(review => review.raterId === userId).map(review => `${userId}:${review.jobId}`),
    ]));
  }

  createRating(request: CreateRatingRequest): Observable<RatingResponse> {
    return this.http.post<ApiResponse<RatingResponse>>(this.baseUrl, request).pipe(
      map((res) => res.data),
      tap(review => {
        this.rememberReviews([review], review.raterId);
        this.reviewsChangedSubject.next();
      }),
    );
  }

  getByJobId(jobId: string): Observable<RatingResponse[]> {
    return this.http.get<ApiResponse<RatingResponse[]>>(`${this.baseUrl}/job/${jobId}`).pipe(
      map((res) => res.data),
      tap(reviews => this.rememberReviews(reviews, this.auth.currentUserId() ?? '')),
    );
  }

  getReceivedByUserId(userId: string, page = 0, size = 20): Observable<PageResponse<RatingResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<ApiResponse<PageResponse<RatingResponse>>>(`${this.baseUrl}/user/${userId}/received`, { params })
      .pipe(map((res) => res.data));
  }

  getGivenByUserId(userId: string, page = 0, size = 20): Observable<PageResponse<RatingResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<ApiResponse<PageResponse<RatingResponse>>>(`${this.baseUrl}/user/${userId}/given`, { params })
      .pipe(map((res) => res.data), tap(page => this.rememberReviews(page.content, userId)));
  }

  getSummary(userId: string): Observable<UserRatingSummary> {
    return this.http.get<ApiResponse<UserRatingSummary>>(`${this.baseUrl}/user/${userId}/summary`).pipe(map((res) => res.data));
  }
}
