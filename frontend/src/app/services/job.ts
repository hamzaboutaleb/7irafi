import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
    CreateJobRequest,
    JobDetail,
    JobImage,
    JobPage,
    JobResponse,
    JobSearchParams,
    UpdateJobRequest,
} from '../types/job.js';

@Injectable({
    providedIn: 'root',
})
export class JobService {
    private readonly http = inject(HttpClient);

    private toHttpParams(search: JobSearchParams): HttpParams {
        let params = new HttpParams()
            .set('page', search.page ?? 0)
            .set('size', search.size ?? 20);

        params = this.setIfPresent(params, 'sort', search.sort);
        params = this.setIfPresent(params, 'skill', search.skill);
        params = this.setIfPresent(params, 'city', search.city);
        params = this.setIfPresent(params, 'status', search.status);
        params = this.setIfPresent(params, 'minBudget', search.minBudget);
        params = this.setIfPresent(params, 'maxBudget', search.maxBudget);
        params = this.setIfPresent(params, 'duration', search.duration);
        params = this.setIfPresent(params, 'preferredTime', search.preferredTime);

        return params;
    }

    private setIfPresent(
        params: HttpParams,
        key: string,
        value: string | number | null | undefined,
    ): HttpParams {
        if (value === null || value === undefined || value === '') {
            return params;
        }

        return params.set(key, value);
    }

    getJobs(search: JobSearchParams = {}) {
        return this.http.get<JobPage>('jobs', {
            params: this.toHttpParams(search),
        });
    }

    getJob(id: string) {
        return this.http.get<JobDetail>(`jobs/${id}`);
    }

    getMyJobs() {
        return this.http.get<JobResponse[]>('jobs/my');
    }

    createJob(request: CreateJobRequest) {
        return this.http.post<JobResponse>('jobs', request);
    }

    updateJob(id: string, request: UpdateJobRequest) {
        return this.http.put<JobResponse>(`jobs/${id}`, request);
    }

    cancelJob(id: string) {
        return this.http.patch<JobResponse>(`jobs/${id}/cancel`, {});
    }

    completeJob(id: string) {
        return this.http.patch<JobResponse>(`jobs/${id}/complete`, {});
    }

    deleteJob(id: string) {
        return this.http.delete<void>(`jobs/${id}`);
    }

    getSkills() {
        return this.http.get<string[]>('jobs/skills');
    }

    getCities() {
        return this.http.get<string[]>('jobs/cities');
    }

    uploadImage(jobId: string, file: File) {
        const formData = new FormData();
        formData.append('file', file);

        return this.http.post<JobImage>(`jobs/${jobId}/images`, formData);
    }

    deleteImage(jobId: string, imageId: string) {
        return this.http.delete<void>(`jobs/${jobId}/images/${imageId}`);
    }
}
