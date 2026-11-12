import { HttpErrorResponse } from '@angular/common/http';
import { DOCUMENT, TitleCasePipe } from '@angular/common';
import { afterNextRender, Component, computed, DestroyRef, HostListener, inject, Injector, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, concatMap, forkJoin, from, of, tap, throwError } from 'rxjs';

import { Footer } from '../footer/footer.js';
import { PostingStatusModal } from '../posting-status-modal/posting-status-modal.js';
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
import { Select, SelectOption } from '../select/select.js';
import { I18nService } from '../../services/i18n.js';
import { LucideDynamicIcon, LucideArrowLeft, LucidePlus, LucideX, LucideImage, LucideLoaderCircle } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-create-job',
  imports: [Navbar, Footer, RouterLink, ReactiveFormsModule, PostingStatusModal, TranslatePipe, Select, LucideDynamicIcon],
  templateUrl: './create-job.html',
})
export class CreateJob implements OnInit {
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly titleCase = new TitleCasePipe();
  readonly icons = { back: LucideArrowLeft, add: LucidePlus, remove: LucideX, image: LucideImage, loading: LucideLoaderCircle };
  readonly skillOptions = computed(() => [{ value: '', label: this.i18n.translate('createJob.selectSkill'), disabled: true }, ...this.skills().map(value => ({ value, label: this.titleCase.transform(value.replaceAll('_', ' ')) }))]);
  readonly cityOptions = computed(() => [{ value: '', label: this.i18n.translate('createJob.selectCity'), disabled: true }, ...this.cities().map(value => ({ value, label: this.titleCase.transform(value.replaceAll('_', ' ')) }))]);
  readonly durationOptions = computed<readonly SelectOption<EstimatedDuration | ''>[]>(() => [
    { value: '', label: this.i18n.translate('createJob.selectDuration'), disabled: true },
    { value: 'HALF_DAY', label: this.i18n.translate('createJob.halfDay') },
    { value: 'FULL_DAY', label: this.i18n.translate('createJob.fullDay') },
    { value: 'MULTI_DAY', label: this.i18n.translate('createJob.multipleDays') },
  ]);
  readonly timeOptions = computed<readonly SelectOption<PreferredTime>[]>(() => [
    { value: 'ANYTIME', label: this.i18n.translate('createJob.anytime') },
    { value: 'MORNING', label: this.i18n.translate('createJob.morning') },
    { value: 'AFTERNOON', label: this.i18n.translate('createJob.afternoon') },
    { value: 'EVENING', label: this.i18n.translate('createJob.evening') },
  ]);
  setChoice(field: 'skill' | 'city', value: string): void {
    const control = this.form.controls[field]; control.setValue(value); control.markAsDirty(); control.markAsTouched();
  }
  setDuration(value: EstimatedDuration | ''): void {
    const control = this.form.controls.estimatedDuration; control.setValue(value || null); control.markAsDirty(); control.markAsTouched();
  }
  setTime(value: PreferredTime): void {
    const control = this.form.controls.preferredTime; control.setValue(value); control.markAsDirty(); control.markAsTouched();
  }
  private focusInvalid(): void {
    afterNextRender(() => {
      const fields = ['title', 'description', 'skill', 'budget', 'city', 'addressLine', 'estimatedDuration', 'preferredTime'] as const;
      const field = fields.find(name => this.form.controls[name].invalid);
      if (field) this.document.getElementById(field)?.focus();
    }, { injector: this.injector });
  }
  private readonly formBuilder = inject(FormBuilder);
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

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
  readonly isRedirecting = signal(false);
  readonly isPosting = computed(() => this.isSaving() || this.isUploading() || this.isRedirecting());
  readonly uploadedPhotoCount = computed(() =>
    this.photos().filter((photo) => photo.status === 'uploaded').length,
  );
  readonly uploadErrors = computed(() => this.photos()
    .filter((photo) => photo.status === 'failed')
    .map((photo) => `${photo.file.name}: ${photo.error}`));
  readonly maxPhotos = 10;
  readonly maxPhotoBytes = 10 * 1024 * 1024;

  constructor() {
    this.destroyRef.onDestroy(() => {
      for (const photo of this.photos()) URL.revokeObjectURL(photo.previewUrl);
    });
  }

  private hasUnfinishedWork(): boolean {
    if (this.isRedirecting()) return false;
    return this.isSaving() || this.isUploading() ||
      (!this.createdJob() && (this.form.dirty || this.photos().length > 0));
  }

  canLeave(): boolean {
    return !this.hasUnfinishedWork() || window.confirm(
      this.i18n.translate('createJob.leaveConfirm'),
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
        errors.push(this.i18n.translate('createJob.photoTypeError', { name: file.name }));
      } else if (!file.size || file.size > this.maxPhotoBytes) {
        errors.push(this.i18n.translate('createJob.photoSizeError', { name: file.name }));
      } else if (photos.length >= this.maxPhotos) {
        errors.push(this.i18n.translate('createJob.photoCountError', { name: file.name }));
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
    if (this.isPosting() || this.createdJob()) return;
    URL.revokeObjectURL(photo.previewUrl);
    this.photos.update((photos) => photos.filter((item) => item !== photo));
    this.photoErrors.set([]);
  }

  private uploadPhotos(): void {
    const job = this.createdJob();
    if (!job || this.isUploading()) return;
    const queued = this.photos().filter((photo) => photo.status === 'queued');
    if (!queued.length) {
      void this.finishPosting();
      return;
    }

    this.isUploading.set(true);
    from(queued).pipe(
      concatMap((photo) => {
        this.updatePhoto(photo, 'uploading');
        return this.jobService.uploadImage(job.id, photo.file).pipe(
          tap(() => this.updatePhoto(photo, 'uploaded')),
          catchError((error: HttpErrorResponse) => {
            const body = error.error as JobApiError | undefined;
            const message = error.status === 0
              ? this.i18n.translate('createJob.uploadUncertain')
              : error.status === 403
                ? this.i18n.translate('createJob.uploadDenied')
                : body?.message ?? this.i18n.translate('createJob.uploadError');
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
    if (!this.createdJob()) return;
    if (this.uploadErrors().length) {
      this.errorMessage.set(this.i18n.translate('jobs.photosFailed'));
      this.showFailure.set(true);
      return;
    }
    await this.openJobs();
  }

  closeFailure(): void {
    this.showFailure.set(false);
    if (this.createdJob()) void this.openJobs();
  }

  private async openJobs(): Promise<void> {
    this.isRedirecting.set(true);
    try {
      if (await this.router.navigate(['/jobs'])) return;
    } catch {
      // Keep the posted job visible when navigation cannot complete.
    }
    this.isRedirecting.set(false);
    this.errorMessage.set(this.i18n.translate('createJob.postedRedirectError'));
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
            this.referenceError.set(this.i18n.translate('createJob.referenceUnavailable'));
          }
          this.isLoading.set(false);
        },
        error: () => {
          this.referenceError.set(this.i18n.translate('createJob.referenceError'));
          this.isLoading.set(false);
        },
      });
  }

  fieldError(field: keyof typeof this.form.controls): string | null {
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) return null;

    if (control.hasError('server')) return control.getError('server') as string;
    if (control.hasError('required')) return this.i18n.translate('createJob.requiredError');
    if (control.hasError('maxlength')) {
      return field === 'title' ? this.i18n.translate('createJob.titleLimit') : this.i18n.translate('createJob.addressLimit');
    }
    if (field === 'budget') return this.i18n.translate('createJob.budgetError');
    return this.i18n.translate('createJob.whitespaceError');
  }

  submit(): void {
    if (this.isSaving() || this.createdJob() || this.isLoading() || this.referenceError()) return;

    this.errorMessage.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.focusInvalid(); return; }

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
            this.i18n.translate('createJob.postUncertain'),
          );
          return;
        }
        if (error.status === 401) {
          this.errorMessage.set(this.i18n.translate('createJob.sessionExpired'));
          return;
        }
        if (error.status === 403) {
          this.errorMessage.set(this.i18n.translate('createJob.postDenied'));
          return;
        }

        const body = error.error as JobApiError | undefined;
        this.errorMessage.set(body?.message ?? this.i18n.translate('createJob.postError'));
        for (const fieldError of body?.fieldErrors ?? []) {
          const control = this.form.get(fieldError.field);
          control?.setErrors({ ...control.errors, server: fieldError.message });
        }
        if (body?.fieldErrors?.length) {
          this.showFailure.set(false);
          this.focusInvalid();
        }
      },
    });
  }
}
