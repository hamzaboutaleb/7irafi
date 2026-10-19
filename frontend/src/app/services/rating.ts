import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

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
  raterName: string;
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
  private baseUrl = '/ratings';

  createRating(request: CreateRatingRequest): Observable<RatingResponse> {
    return this.http.post<ApiResponse<RatingResponse>>(this.baseUrl, request).pipe(map((res) => res.data));
  }

  getByJobId(jobId: string): Observable<RatingResponse[]> {
    return this.http.get<ApiResponse<RatingResponse[]>>(`${this.baseUrl}/job/${jobId}`).pipe(map((res) => res.data));
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
      .pipe(map((res) => res.data));
  }

  getSummary(userId: string): Observable<UserRatingSummary> {
    return this.http.get<ApiResponse<UserRatingSummary>>(`${this.baseUrl}/user/${userId}/summary`).pipe(map((res) => res.data));
  }
}