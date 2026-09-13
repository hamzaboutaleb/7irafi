import { HttpErrorResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, concatMap, forkJoin, from, of, tap, throwError } from 'rxjs';

import { Footer } from '../footer/footer.js';
import { PostingStatusModal } from '../posting-status-modal/posting-status-modal.js';
import { JobPostingResult } from '../../services/job-posting-result.js';
import { Navbar } from '../navbar/navbar.js';
import { JobService } from '../../services/job.js';
import {
  CreateJobRequest,
  EstimatedDuration,
  JobApiError,
  JobResponse,
  PreferredTime,
} from '../../types/job.js';
import { JobPhoto } from '../../types/job.js';

@Component({
  selector: 'app-create-job',
  imports: [Navbar, Footer, RouterLink, ReactiveFormsModule, PostingStatusModal, NgTemplateOutlet],
  templateUrl: './create-job.html',
})
export class CreateJob implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly postingResult = inject(JobPostingResult);
  private redirecting = false;

  readonly skills = signal<string[]>([]);
  readonly cities = signal<string[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly referenceError = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly createdJob = signal<JobResponse | null>(null);
  readonly showFailure = signal(false);
  readonly photos = signal<JobPhoto[]>([]);
  readonly photoErrors = signal<string[]>([]);
  readonly isUploading = signal(false);
  readonly failedPhotos = computed(() => this.photos().filter((photo) => photo.status === 'failed'));
  readonly maxPhotos = 10;
  readonly maxPhotoBytes = 10 * 1024 * 1024;

  constructor() {
    this.destroyRef.onDestroy(() => {
      for (const photo of this.photos()) URL.revokeObjectURL(photo.previewUrl);
    });
  }

  private hasUnfinishedWork(): boolean {
    if (this.redirecting) return false;
    return this.isSaving() || this.isUploading() || this.failedPhotos().length > 0 ||
      (!this.createdJob() && (this.form.dirty || this.photos().length > 0));
  }

  canLeave(): boolean {
    return !this.hasUnfinishedWork() || window.confirm(
      'Leave this page? Unsaved details and photos will be lost. Any job already posted will remain.',
    );
  }

  @HostListener('window:beforeunload', ['$event'])
  beforeUnload(event: BeforeUnloadEvent): void {
    if (this.hasUnfinishedWork()) event.preventDefault();
  }

  selectPhotos(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (this.isSaving() || this.createdJob()) return;
    const errors: string[] = [];
    const photos = [...this.photos()];
    for (const file of Array.from(input.files ?? [])) {
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
        errors.push(`${file.name}: choose a JPEG, PNG, or WebP photo.`);
      } else if (!file.size || file.size > this.maxPhotoBytes) {
        errors.push(`${file.name}: choose a non-empty photo no larger than 10 MB.`);
      } else if (photos.length >= this.maxPhotos) {
        errors.push(`${file.name}: you can add up to 10 photos.`);
      } else if (!photos.some((photo) => photo.file.name === file.name &&
        photo.file.size === file.size && photo.file.lastModified === file.lastModified)) {
        photos.push({ file, previewUrl: URL.createObjectURL(file), status: 'queued', error: null });
      }
    }
    this.photos.set(photos);
    this.photoErrors.set(errors);
    input.value = '';
  }

  removePhoto(photo: JobPhoto): void {
    if (this.isSaving() || this.isUploading() || photo.status === 'uploaded') return;
    URL.revokeObjectURL(photo.previewUrl);
    this.photos.update((photos) => photos.filter((item) => item !== photo));
    this.photoErrors.set([]);
  }

  uploadPhotos(): void {
    const job = this.createdJob();
    if (!job || this.isUploading()) return;
    const pending = this.photos().filter((photo) => photo.status !== 'uploaded');
    if (!pending.length) {
      void this.finishPosting();
      return;
    }

    this.isUploading.set(true);
    from(pending).pipe(
      concatMap((photo) => {
        this.updatePhoto(photo, 'uploading');
        return this.jobService.uploadImage(job.id, photo.file).pipe(
          tap(() => this.updatePhoto(photo, 'uploaded')),
          catchError((error: HttpErrorResponse) => {
            const body = error.error as JobApiError | undefined;
            const message = error.status === 0
              ? 'Upload could not be confirmed. View your job before retrying to avoid a duplicate photo.'
              : error.status === 403
                ? 'You do not have permission to upload this photo.'
                : body?.message ?? 'Photo upload failed. Please retry.';
            this.updatePhoto(photo, 'failed', message);
            if (error.status === 401) return throwError(() => error);
            return of(null);
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      error: () => this.isUploading.set(false),
      complete: () => {
        this.isUploading.set(false);
        if (!this.destroyRef.destroyed) void this.finishPosting();
      },
    });
  }

  private async finishPosting(): Promise<void> {
    const job = this.createdJob();
    if (!job) return;
    this.postingResult.set({
      jobId: job.id,
      failed: this.failedPhotos().map((photo) => ({
        file: photo.file, error: photo.error ?? 'Photo upload failed.',
      })),
    });
    this.redirecting = true;
    try {
      if (await this.router.navigate(['/jobs'])) return;
    } catch {
      // Preserve the posted job and retry files when navigation cannot complete.
    }
    this.redirecting = false;
    this.errorMessage.set('Your job was posted. Open the jobs page to see its status.');
    this.showFailure.set(true);
  }

  private updatePhoto(photo: JobPhoto, status: JobPhoto['status'], error: string | null = null): void {
    this.photos.update((photos) => photos.map((item) =>
      item.file === photo.file ? { ...item, status, error } : item,
    ));
  }

  readonly form = this.formBuilder.nonNullable.group({
    title: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
    description: ['', [Validators.required, Validators.pattern(/\S/)]],
    skill: ['', Validators.required],
    city: ['', Validators.required],
    addressLine: ['', Validators.maxLength(200)],
    budget: this.formBuilder.control<number | null>(null, [
      Validators.required,
      Validators.min(0.01),
      Validators.max(99999999.99),
      Validators.pattern(/^\d+(\.\d{1,2})?$/),
    ]),
    estimatedDuration: this.formBuilder.control<EstimatedDuration | null>(null, Validators.required),
    preferredTime: this.formBuilder.nonNullable.control<PreferredTime>('ANYTIME'),
  });

  ngOnInit(): void {
    this.loadReferences();
  }

  loadReferences(): void {
    if (this.isLoading()) return;

    this.isLoading.set(true);
    this.referenceError.set(null);

    forkJoin({ skills: this.jobService.getSkills(), cities: this.jobService.getCities() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ skills, cities }) => {
          this.skills.set(skills);
          this.cities.set(cities);
          if (!skills.length || !cities.length) {
            this.referenceError.set('Job posting is temporarily unavailable. Please try again.');
          }
          this.isLoading.set(false);
        },
        error: () => {
          this.referenceError.set('Unable to load skills and cities. Please try again.');
          this.isLoading.set(false);
        },
      });
  }

  fieldError(field: keyof typeof this.form.controls): string | null {
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) return null;

    if (control.hasError('server')) return control.getError('server') as string;
    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('maxlength')) {
      return field === 'title' ? 'Use 120 characters or fewer.' : 'Use 200 characters or fewer.';
    }
    if (field === 'budget') return 'Enter 0.01–99,999,999.99 MAD, with at most two decimal places.';
    return 'Enter more than whitespace.';
  }

  submit(): void {
    if (this.isSaving() || this.createdJob() || this.isLoading() || this.referenceError()) return;

    this.errorMessage.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    const value = this.form.getRawValue();
    if (value.budget === null || value.estimatedDuration === null) return;

    const request: CreateJobRequest = {
      title: value.title.trim(),
      description: value.description.trim(),
      skill: value.skill,
      city: value.city,
      addressLine: value.addressLine.trim() || null,
      budget: value.budget,
      estimatedDuration: value.estimatedDuration,
      preferredTime: value.preferredTime,
    };

    this.isSaving.set(true);
    this.jobService.createJob(request).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (job) => {
        this.createdJob.set(job);
        this.isSaving.set(false);
        this.uploadPhotos();
      },
      error: (error: HttpErrorResponse) => {
        this.isSaving.set(false);
        this.showFailure.set(true);
        if (error.status === 0) {
          this.errorMessage.set(
            'Unable to confirm whether your job was posted. Check the jobs page before trying again.',
          );
          return;
        }
        if (error.status === 401) {
          this.errorMessage.set('Your session has expired. Please sign in again.');
          return;
        }
        if (error.status === 403) {
          this.errorMessage.set('You do not have permission to post a job.');
          return;
        }

        const body = error.error as JobApiError | undefined;
        this.errorMessage.set(body?.message ?? 'Unable to post your job. Please try again.');
        for (const fieldError of body?.fieldErrors ?? []) {
          const control = this.form.get(fieldError.field);
          control?.setErrors({ ...control.errors, server: fieldError.message });
        }
      },
    });
  }
}
