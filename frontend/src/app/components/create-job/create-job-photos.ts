import { HttpErrorResponse } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, concatMap, from, of, tap, throwError } from 'rxjs';

import { I18nService } from '../../services/i18n.js';
import { JobService } from '../../services/job.js';
import { JobApiError, JobPhoto } from '../../types/job.js';

@Injectable()
export class CreateJobPhotos {
  private readonly i18n = inject(I18nService);
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly photoState = signal<JobPhoto[]>([]);
  private readonly errorState = signal<string[]>([]);
  private readonly uploadingState = signal(false);

  readonly photos = this.photoState.asReadonly();
  readonly errors = this.errorState.asReadonly();
  readonly isUploading = this.uploadingState.asReadonly();
  readonly uploadedCount = computed(
    () => this.photos().filter((photo) => photo.status === 'uploaded').length,
  );
  readonly uploadErrors = computed(() =>
    this.photos()
      .filter((photo) => photo.status === 'failed')
      .map((photo) => `${photo.file.name}: ${photo.error}`),
  );
  readonly maxPhotos = 10;
  private readonly maxPhotoBytes = 10 * 1024 * 1024;

  constructor() {
    this.destroyRef.onDestroy(() => {
      for (const photo of this.photos()) URL.revokeObjectURL(photo.previewUrl);
    });
  }

  select(files: readonly File[]): void {
    if (this.isUploading()) return;
    const errors: string[] = [];
    const photos = [...this.photos()];
    for (const file of files) {
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
        errors.push(this.i18n.translate('createJob.photoTypeError', {
          name: file.name
        }));
      } else if (!file.size || file.size > this.maxPhotoBytes) {
        errors.push(this.i18n.translate('createJob.photoSizeError', {
          name: file.name
        }));
      } else if (photos.length >= this.maxPhotos) {
        errors.push(this.i18n.translate('createJob.photoCountError', {
          name: file.name
        }));
      } else if (
        !photos.some(
          (photo) =>
            photo.file.name === file.name &&
            photo.file.size === file.size &&
            photo.file.lastModified === file.lastModified,
        )) {
        photos.push({
          file,
          previewUrl: URL.createObjectURL(file),
          status: 'queued',
          error: null
        });
      }
    }
    this.photoState.set(photos);
    this.errorState.set(errors);
  }

  remove(photo: JobPhoto): void {
    if (this.isUploading()) return;
    URL.revokeObjectURL(photo.previewUrl);
    this.photoState.update((photos) => photos.filter((item) => item !== photo));
    this.errorState.set([]);
  }

  upload(jobId: string, completed: () => void): void {
    if (this.isUploading()) return;
    const queued = this.photos().filter((photo) => photo.status === 'queued');
    if (!queued.length) {
      completed();
      return;
    }

    this.uploadingState.set(true);
    from(queued)
      .pipe(
        concatMap((photo) => {
          this.updatePhoto(photo, 'uploading');
          return this.jobService.uploadImage(jobId, photo.file).pipe(
            tap(() => this.updatePhoto(photo, 'uploaded')),
            catchError((error: HttpErrorResponse) => {
              const body = error.error as JobApiError | undefined;
              const message =
                error.status === 0
                  ? this.i18n.translate('createJob.uploadUncertain')
                  : error.status === 403
                    ? this.i18n.translate('createJob.uploadDenied')
                    : (body?.message ?? this.i18n.translate('createJob.uploadError'));
              this.updatePhoto(photo, 'failed', message);
              if (error.status === 401) return throwError(() => error);
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        error: () => this.uploadingState.set(false),
        complete: () => {
          this.uploadingState.set(false);
          if (!this.destroyRef.destroyed) completed();
        },
      });
  }

  private updatePhoto(
    photo: JobPhoto,
    status: JobPhoto['status'],
    error: string | null = null,
  ): void {
    this.photoState.update((photos) =>
      photos.map((item) => (item.file === photo.file ? { ...item, status, error } : item)),
    );
  }
}
