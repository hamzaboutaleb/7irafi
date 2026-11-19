import type { TranslationKey } from '../i18n/language.js';
import type { ApplicationStatus } from '../types/application.js';
import type {
  EstimatedDuration,
  JobAction,
  JobFilters,
  JobResponse,
  JobStatus,
  PreferredTime,
} from '../types/job.js';

export const jobDurationLabels = {
  HALF_DAY: 'createJob.halfDay',
  FULL_DAY: 'createJob.fullDay',
  MULTI_DAY: 'createJob.multipleDays',
} as const satisfies Record<EstimatedDuration, TranslationKey>;

export const jobTimeLabels = {
  MORNING: 'createJob.morning',
  AFTERNOON: 'createJob.afternoon',
  EVENING: 'createJob.evening',
  ANYTIME: 'createJob.anytime',
} as const satisfies Record<PreferredTime, TranslationKey>;

export const jobStatusLabels = {
  OPEN: 'dashboard.status.OPEN',
  ASSIGNED: 'dashboard.status.ASSIGNED',
  COMPLETED: 'dashboard.status.COMPLETED',
  CANCELLED: 'dashboard.status.CANCELLED',
} as const satisfies Record<JobStatus, TranslationKey>;

export const jobDisplayStatusLabels = {
  ...jobStatusLabels,
  PENDING: 'dashboard.status.PENDING',
  ACCEPTED: 'dashboard.status.ACCEPTED',
  NOT_SELECTED: 'dashboard.status.NOT_SELECTED',
  CLOSED: 'dashboard.status.CLOSED',
} as const satisfies Record<JobStatus | ApplicationStatus, TranslationKey>;

export const jobDurations = Object.keys(jobDurationLabels) as readonly EstimatedDuration[];
export const jobTimes = Object.keys(jobTimeLabels) as readonly PreferredTime[];
export const jobStatuses = Object.keys(jobStatusLabels) as readonly JobStatus[];

export const emptyFilters = (): JobFilters => ({
  skill: '',
  city: '',
  minBudget: null,
  maxBudget: null,
  duration: '',
  preferredTime: '',
});

export function canPerformJobAction(job: JobResponse, action: JobAction): boolean {
  switch (action) {
    case 'edit':
    case 'cancel':
      return job.status === 'OPEN';
    case 'complete':
      return job.status === 'ASSIGNED';
    case 'delete':
      return job.status !== 'ASSIGNED';
  }
}
