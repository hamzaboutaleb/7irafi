import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import { AnalyticsQuery, AnalyticsResponse } from '../types/analytics.js';

@Injectable({ providedIn: 'root' })
export class AnalyticsApi {
  private readonly http = inject(HttpClient);

  jobs(query: AnalyticsQuery) {
    return this.http.get<AnalyticsResponse>('analytics/my/jobs', {
      params: { ...query, page: 0, size: 1 },
    }).pipe(map(response => validateResponse(response)));
  }

  applications(query: AnalyticsQuery) {
    return this.http.get<AnalyticsResponse>('analytics/my/applications', {
      params: { ...query, page: 0, size: 1 },
    }).pipe(map(response => validateResponse(response)));
  }
}

/** A malformed response is a query failure, never an empty chart or a zero total. */
function validateResponse(response: AnalyticsResponse): AnalyticsResponse {
  if (!response || !Number.isSafeInteger(response.total) || response.total < 0 || !Array.isArray(response.activity)) {
    throw new Error('Invalid analytics response');
  }
  const activity = response.activity;
  const valid = activity.every(item => item && typeof item.value === 'string' &&
    /^\d{4}-\d{2}-\d{2}T00:00(?::00)?$/.test(item.value) && Number.isSafeInteger(item.count) && item.count >= 0);
  if (!valid || activity.reduce((sum, item) => sum + item.count, 0) !== response.total ||
      new Set(activity.map(item => item.value)).size !== activity.length) {
    throw new Error('Invalid analytics counts');
  }
  return response;
}
