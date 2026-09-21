import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, concatMap, from, of, tap, throwError } from 'rxjs';
import { AuthService } from './auth.js';
import { JobService } from './job.js';

interface PostingResult {
  jobId: string;
  failed: { file: File; error: string }[];
}

@Injectable({ providedIn: 'root' })
export class JobPostingResult {
  private readonly jobService = inject(JobService);
  private readonly auth = inject(AuthService);
  private ownerToken: string | null = null;
  readonly result = signal<PostingResult | null>(null);
  readonly busy = signal(false);

  set(result: PostingResult): void {
    this.ownerToken = this.auth.getToken();
    this.result.set(result);
  }

  clear(): void {
    if (!this.busy()) this.result.set(null);
  }

  clearIfSessionChanged(): void {
    if (this.ownerToken !== this.auth.getToken()) this.result.set(null);
  }

  retry(onComplete: () => void): void {
    this.clearIfSessionChanged();
    const result = this.result();
    if (!result || this.busy()) return;
    this.busy.set(true);
    from(result.failed).pipe(
      concatMap((photo) => this.jobService.uploadImage(result.jobId, photo.file).pipe(
        tap(() => this.result.update((current) => current ? {
          ...current, failed: current.failed.filter((item) => item.file !== photo.file),
        } : null)),
        catchError((error: HttpErrorResponse) => {
          if (error.status === 401) {
            this.result.set(null);
            return throwError(() => error);
          }
          const message = error.status === 0
            ? 'Upload could not be confirmed. Check the job before retrying to avoid duplicate photos.'
            : 'Photo upload failed. Please try again.';
          this.result.update((current) => current ? { ...current,
            failed: current.failed.map((item) => item.file === photo.file ? { ...item, error: message } : item),
          } : null);
          return of(null);
        }),
      )),
    ).subscribe({
      error: () => this.busy.set(false),
      complete: () => {
        this.busy.set(false);
        onComplete();
      },
    });
  }
}
