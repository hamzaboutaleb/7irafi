export type Granularity = 'day' | 'week' | 'month';

export interface AnalyticsCount {
  value: string;
  count: number;
}

// Only the response fields used by the dashboard.
export interface AnalyticsResponse {
  total: number;
  activity: AnalyticsCount[];
}

export interface AnalyticsQuery {
  from: string;
  to: string;
  granularity: Granularity;
}
