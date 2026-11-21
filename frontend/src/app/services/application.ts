import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { ApplicationPage, JobApplication } from '../types/application.js';

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private readonly http = inject(HttpClient);

  apply(jobId: string) {
    return this.http.post<JobApplication>('applications', { jobId });
  }

  getMine(page = 0) {
    return this.http.get<ApplicationPage>('applications/my', { params: { page, size: 10 } });
  }

  getMyApplicationStatus(jobId: string) {
    return this.http.get<{ applied: boolean }>('applications/my/status', {
      params: { jobId },
    });
  }

  getApplicants(jobId: string, page = 0) {
    return this.http.get<ApplicationPage>(`applications/job/${jobId}`, {
      params: { page, size: 10 },
    });
  }

  accept(applicationId: string) {
    return this.http.patch<JobApplication>(`applications/${applicationId}/accept`, null);
  }
}

export function applicationError(error: HttpErrorResponse, fallback: string): string {
  if (error.status === 0 || error.status === 503) {
    return 'Unable to reach the service. Please try again.';
  }
  const messages: Record<string, string> = {
    WORKER_REQUIRED: 'Only workers with an active professional profile can apply.',
    OWN_JOB_APPLICATION: 'You cannot apply to your own job.',
    JOB_NOT_OPEN: 'This job is no longer accepting applications.',
    JOB_NOT_FOUND: 'This job is no longer available.',
    JOB_ACCESS_DENIED: 'Only the job owner can view or accept applicants.',
    APPLICATION_NOT_FOUND: 'This application is no longer available.',
  };
  return messages[error.error?.code] ?? fallback;
}
