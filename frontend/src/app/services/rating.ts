import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

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
    throw new Error('Not implemented');
  }

  getByJobId(jobId: string): Observable<RatingResponse[]> {
    throw new Error('Not implemented');
  }

  getReceivedByUserId(userId: string, page?: number, size?: number): Observable<PageResponse<RatingResponse>> {
    throw new Error('Not implemented');
  }

  getGivenByUserId(userId: string, page?: number, size?: number): Observable<PageResponse<RatingResponse>> {
    throw new Error('Not implemented');
  }

  getSummary(userId: string): Observable<UserRatingSummary> {
    throw new Error('Not implemented');
  }
}