import { Page } from './api.js';

export type JobStatus = 'OPEN' | 'ASSIGNED' | 'COMPLETED' | 'CANCELLED';

export type EstimatedDuration = 'HALF_DAY' | 'FULL_DAY' | 'MULTI_DAY';

export type PreferredTime = 'MORNING' | 'AFTERNOON' | 'EVENING' | 'ANYTIME';

export interface JobListItem {
  id: string;
  clientId: string;
  title: string;
  description: string;
  skill: string;
  city: string;
  budget: number;
  estimatedDuration: EstimatedDuration;
  preferredTime: PreferredTime;
  status: JobStatus;
  assignedWorkerId: string | null;
  createdAt: string;
  updatedAt: string;
  thumbnailUrl: string | null;
}

export interface JobResponse extends Omit<JobListItem, 'thumbnailUrl'> {
  addressLine: string | null;
}

export interface JobImage {
  id: string;
  url: string;
}

export interface JobDetail extends JobResponse {
  images: JobImage[];
}

export interface CreateJobRequest {
  title: string;
  description: string;
  skill: string;
  city: string;
  addressLine?: string | null;
  budget: number;
  estimatedDuration: EstimatedDuration;
  preferredTime?: PreferredTime | null;
}

export interface UpdateJobRequest {
  title: string;
  description: string;
  addressLine?: string | null;
}

export interface JobSearchParams {
  page?: number;
  size?: number;
  sort?: string;
  skill?: string | null;
  city?: string | null;
  status?: JobStatus | null;
  minBudget?: number | null;
  maxBudget?: number | null;
  duration?: EstimatedDuration | null;
  preferredTime?: PreferredTime | null;
}

export type JobPage = Page<JobListItem>;

export interface JobApiError {
  message: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface JobPhoto {
  file: File;
  previewUrl: string;
  status: 'queued' | 'uploading' | 'uploaded' | 'failed';
  error: string | null;
}
