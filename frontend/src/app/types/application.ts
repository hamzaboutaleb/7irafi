import { Page } from './api.js';

export type ApplicationStatus = 'PENDING' | 'ACCEPTED' | 'NOT_SELECTED' | 'CLOSED';

export interface JobApplication {
  id: string;
  jobId: string;
  applicantId: string;
  createdAt: string;
  status: ApplicationStatus;
}

export type ApplicationPage = Page<JobApplication>;

export const applicationStatusLabels: Record<ApplicationStatus, string> = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  NOT_SELECTED: 'Not selected',
  CLOSED: 'Closed',
};
