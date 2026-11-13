import { JobAction, JobResponse } from '../types/job.js';

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
