import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { forkJoin, map, of, switchMap } from 'rxjs';

import { Footer } from '../../components/footer/footer.js';
import { JobCard } from '../../components/Job-card/job-card.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { JobService } from '../../services/job.js';
import { JobImage, JobListItem } from '../../types/job.js';

interface JobCardData {
    job: JobListItem;
    images: JobImage[];
}

@Component({
    selector: 'app-job',
    imports: [Navbar, Footer, JobCard],
    templateUrl: './job.html',
})
export class Job implements OnInit {
    private readonly jobService = inject(JobService);

    readonly jobCards = signal<JobCardData[]>([]);
    readonly isLoading = signal(true);
    readonly errorMessage = signal<string | null>(null);

    ngOnInit(): void {
        this.loadJobs();
    }

    loadJobs(): void {
        this.isLoading.set(true);
        this.errorMessage.set(null);

        this.jobService
            .getJobs({ page: 0, size: 12 })
            .pipe(
                switchMap((page) => {
                    if (page.content.length === 0) {
                        return of([]);
                    }

                    return forkJoin(
                        page.content.map((job) =>
                            this.jobService.getJob(job.id).pipe(
                                map((detail) => ({
                                    job,
                                    images: detail.images,
                                })),
                            ),
                        ),
                    );
                }),
            )
            .subscribe({
                next: (jobCards) => {
                    this.jobCards.set(jobCards);
                    this.isLoading.set(false);
                },
                error: (err: HttpErrorResponse) => {
                    this.errorMessage.set(this.getErrorMessage(err));
                    this.isLoading.set(false);
                },
            });
    }

    private getErrorMessage(err: HttpErrorResponse): string {
        if (err.status === 0) {
            return 'The server cannot be reached. Make sure the API gateway is running.';
        }

        return err.error?.message ?? err.error?.error?.message ?? 'Unable to load jobs.';
    }
}
