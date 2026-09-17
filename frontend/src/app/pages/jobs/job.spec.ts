import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, Subject } from 'rxjs';
import { Job } from './job';
import { JobService } from '../../services/job';
import { JobPostingResult } from '../../services/job-posting-result';
import { JobPage } from '../../types/job';

const page: JobPage = { content: [], page: 0, size: 12, totalElements: 24, totalPages: 2 };

describe('Job filters', () => {
  let component: Job;
  const service = {
    getJobs: vi.fn(() => of(page)),
    getSkills: vi.fn(() => of(['Plumbing'])),
    getCities: vi.fn(() => of(['Casablanca'])),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    service.getJobs.mockReturnValue(of(page));
    TestBed.configureTestingModule({ providers: [
      { provide: JobService, useValue: service },
      { provide: JobPostingResult, useValue: { result: signal(null), clearIfSessionChanged: vi.fn() } },
    ] });
    component = TestBed.runInInjectionContext(() => new Job());
  });

  it('applies desktop filters on page zero and preserves them when paging and sorting', () => {
    component.currentPage.set(3);
    component.updateFilter('city', 'Casablanca', false);
    expect(service.getJobs).toHaveBeenLastCalledWith(expect.objectContaining({ city: 'Casablanca', page: 0 }));
    component.changePage(1);
    expect(service.getJobs).toHaveBeenLastCalledWith(expect.objectContaining({ city: 'Casablanca', page: 1 }));
    component.changeSort('budget,asc');
    expect(service.getJobs).toHaveBeenLastCalledWith(expect.objectContaining({ city: 'Casablanca', page: 0, sort: 'budget,asc' }));
  });

  it('keeps mobile edits and reset as drafts until applied', () => {
    component.updateFilter('city', 'Casablanca', true);
    expect(service.getJobs).not.toHaveBeenCalled();
    expect(component.filters().city).toBe('');
    component.applyFilters();
    expect(component.filters().city).toBe('Casablanca');
    component.clearFilters(true);
    expect(component.filters().city).toBe('Casablanca');
    component.applyFilters();
    expect(component.filters().city).toBe('');
  });

  it('rejects negative and reversed budgets but accepts zero', () => {
    component.updateFilter('minBudget', -1, false);
    expect(service.getJobs).not.toHaveBeenCalled();
    component.updateFilter('minBudget', 500, true);
    component.updateFilter('maxBudget', 100, false);
    expect(service.getJobs).not.toHaveBeenCalled();
    expect(component.budgetError()).toBeTruthy();
    component.updateFilter('minBudget', 0, false);
    expect(component.budgetError()).toBeNull();
    expect(service.getJobs).toHaveBeenCalledWith(expect.objectContaining({ minBudget: 0, maxBudget: 100 }));
  });

  it('removes one chip without clearing the other filters', () => {
    component.updateFilter('city', 'Casablanca', true);
    component.updateFilter('skill', 'Plumbing', false);
    component.removeFilter('city');
    expect(component.filters().skill).toBe('Plumbing');
    expect(component.chips()).toEqual([{ key: 'skill', label: 'plumbing' }]);
  });

  it('ignores stale requests after filters change', () => {
    const stale = new Subject<JobPage>();
    service.getJobs.mockReturnValueOnce(stale);
    component.loadJobs();
    component.updateFilter('city', 'Casablanca', false);
    stale.next({ ...page, totalElements: 99 });
    expect(component.totalJobs()).toBe(24);
    expect(component.isLoading()).toBe(false);
  });
});
