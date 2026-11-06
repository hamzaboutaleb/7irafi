import { inject, Injectable } from '@angular/core';
import { catchError, forkJoin, map, Observable, of, startWith, switchMap } from 'rxjs';
import { ApplicationService } from './application.js';
import { JobService } from './job.js';
import { RatingService } from './rating.js';
import { ApplicationStatus } from '../types/application.js';
import { JobResponse } from '../types/job.js';

export type DashboardView = 'hiring' | 'working';

export interface DashboardJob {
  id: string;
  job: JobResponse | null;
  applicationStatus?: ApplicationStatus;
  appliedAt?: string;
}

export interface DashboardAction {
  jobId: string;
  title: string;
  kind: 'applicants' | 'review' | 'accepted';
  count?: number;
}

export interface DashboardSnapshot {
  jobs: DashboardJob[];
  actions: DashboardAction[];
  partial: boolean;
}

interface ActionCheck {
  action: DashboardAction | null;
  unavailable: boolean;
}

/**
 * Builds the dashboard from existing APIs. Each view has a small preview, not a
 * second copy of the full jobs page. Optional checks can fail without hiding jobs.
 * Provided by the dashboard component so nothing is cached across accounts.
 */
@Injectable()
export class DashboardData {
  private readonly jobs = inject(JobService);
  private readonly applications = inject(ApplicationService);
  private readonly ratings = inject(RatingService);

  loadHiring(userId: string): Observable<DashboardSnapshot> {
    return this.jobs.getMyJobs().pipe(
      switchMap(jobs => {
        const recent = [...jobs].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
        const active = recent.filter(job => job.status === 'OPEN' || job.status === 'ASSIGNED');
        const completed = recent.filter(job => job.status === 'COMPLETED');
        const preview = [...active, ...completed].slice(0, 3).map(job => ({ id: job.id, job }));
        // Bound extra requests to three open jobs and three completed jobs.
        const checks = [
          ...recent.filter(job => job.status === 'OPEN').slice(0, 3).map(job => this.checkApplicants(job)),
          ...completed.filter(job => job.assignedWorkerId).slice(0, 3).map(job => this.checkReview(job, userId)),
        ];
        return this.combine(preview, checks);
      }),
    );
  }

  loadWorking(userId: string): Observable<DashboardSnapshot> {
    // The application API returns ten recent entries. The UI says "recent" and
    // always links to the full list; it does not present these as account totals.
    return this.applications.getMine().pipe(
      switchMap(page => {
        const requests = page.content.map(application => this.jobs.getJob(application.jobId).pipe(
          map((job): DashboardJob => ({
            id: application.jobId, job, applicationStatus: application.status, appliedAt: application.createdAt,
          })),
          // Keep a link to the application when its job was deleted or unavailable.
          catchError(() => of<DashboardJob>({
            id: application.jobId, job: null, applicationStatus: application.status, appliedAt: application.createdAt,
          })),
        ));
        return requests.length ? forkJoin(requests) : of<DashboardJob[]>([]);
      }),
      switchMap(rows => {
        const sorted = [...rows].sort((a, b) => this.workPriority(a, userId) - this.workPriority(b, userId));
        const participantJobs = rows.flatMap(row => row.job?.assignedWorkerId === userId ? [row.job] : []);
        const assigned = participantJobs.filter(job => job.status === 'ASSIGNED');
        const completed = participantJobs.filter(job => job.status === 'COMPLETED');
        const checks = completed.slice(0, 3).map(job => this.checkReview(job, userId));
        return this.combine(sorted.slice(0, 3), checks).pipe(map(snapshot => ({
          ...snapshot,
          partial: snapshot.partial || rows.some(row => !row.job),
          actions: [
            ...assigned.map((job): DashboardAction => ({ jobId: job.id, title: job.title, kind: 'accepted' })),
            ...snapshot.actions,
          ].slice(0, 4),
        })));
      }),
    );
  }

  private workPriority(row: DashboardJob, userId: string): number {
    if (row.job?.status === 'ASSIGNED' && row.job.assignedWorkerId === userId) return 0;
    if (row.applicationStatus === 'PENDING' && row.job?.status === 'OPEN') return 1;
    if (row.job?.status === 'COMPLETED' && row.job.assignedWorkerId === userId) return 2;
    return 3;
  }

  private checkApplicants(job: JobResponse): Observable<ActionCheck> {
    return this.applications.getApplicants(job.id).pipe(
      map(page => ({
        action: page.totalElements > 0
          ? { jobId: job.id, title: job.title, kind: 'applicants' as const, count: page.totalElements }
          : null,
        unavailable: false,
      })),
      catchError(() => of({ action: null, unavailable: true })),
    );
  }

  private checkReview(job: JobResponse, userId: string): Observable<ActionCheck> {
    return this.ratings.getByJobId(job.id).pipe(
      map(reviews => ({
        action: reviews.some(review => review.raterId === userId)
          ? null : { jobId: job.id, title: job.title, kind: 'review' as const },
        unavailable: false,
      })),
      catchError(() => of({ action: null, unavailable: true })),
    );
  }

  private combine(jobs: DashboardJob[], checks: Observable<ActionCheck>[]): Observable<DashboardSnapshot> {
    const initial: DashboardSnapshot = { jobs, actions: [], partial: false };
    if (!checks.length) return of(initial);
    // Show jobs immediately. Optional reminder lookups must not hold up the list.
    return forkJoin(checks).pipe(
      map(items => ({
        jobs,
        actions: items.flatMap(item => item.action ? [item.action] : []).slice(0, 4),
        partial: items.some(item => item.unavailable),
      })),
      startWith(initial),
    );
  }
}
