import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Applications } from './applications.js';
import { ApplicationService } from '../../services/application.js';
import { JobService } from '../../services/job.js';

const application = { id: 'application', jobId: 'job', status: 'CLOSED', createdAt: '2026-09-16' };
const page = { content: [application], page: 0, size: 10, totalElements: 1, totalPages: 1 };

describe('My applications', () => {
  const service = { getMine: vi.fn() };
  const jobs = { getJob: vi.fn(), getMyJobs: vi.fn() };
  beforeEach(() => {
    vi.resetAllMocks();
    service.getMine.mockReturnValue(of(page));
    jobs.getJob.mockReturnValue(of({ title: 'Fix the sink', images: [] }));
    jobs.getMyJobs.mockReturnValue(of([]));
    TestBed.configureTestingModule({ providers: [
      { provide: ApplicationService, useValue: service },
      { provide: JobService, useValue: jobs },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
    ] });
  });

  function create() {
    const component = TestBed.createComponent(Applications).componentInstance;
    component.ngOnInit();
    return component;
  }

  it('keeps a closed application visible when its job has been deleted', () => {
    jobs.getJob.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const component = create();
    expect(component.rows()[0].status).toBe('CLOSED');
    expect(component.rows()[0].jobMissing).toBe(true);
    expect(component.rows()[0].jobTitle).toBe('Job no longer available');
    expect(component.error()).toBeNull();
  });

  it('preserves the application and retryable detail access during a detail outage', () => {
    jobs.getJob.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const component = create();
    expect(component.rows()[0].jobTitle).toBe('Job details unavailable');
    expect(component.rows()[0].jobMissing).toBe(false);
  });

  it('shows an empty page without waiting for job requests', () => {
    service.getMine.mockReturnValue(of({ ...page, content: [], totalElements: 0, totalPages: 0 }));
    const component = create();
    expect(component.loading()).toBe(false);
    expect(component.rows()).toEqual([]);
    expect(jobs.getJob).not.toHaveBeenCalled();
  });

  it('loads the requested page and lets an owner load posted jobs', () => {
    const component = create();
    component.load(1);
    expect(service.getMine).toHaveBeenLastCalledWith(1);
    component.show('jobs');
    expect(jobs.getMyJobs).toHaveBeenCalledOnce();
    expect(component.view()).toBe('jobs');
  });
});
