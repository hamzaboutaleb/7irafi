import { HttpErrorResponse } from '@angular/common/http';
import { TitleCasePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Footer } from '../footer/footer.js';
import { PostingStatusModal } from '../posting-status-modal/posting-status-modal.js';
import { Navbar } from '../navbar/navbar.js';
import { JobService } from '../../services/job.js';
import {
  CreateJobRequest,
  EstimatedDuration,
  JobResponse,
  JobPhoto,
  PreferredTime,
} from '../../types/job.js';
import { Select, SelectOption } from '../select/select.js';
import { I18nService } from '../../services/i18n.js';
import { LucideDynamicIcon, LucideArrowLeft, LucideLoaderCircle } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { JobPhotoPicker } from '../job-photo-picker/job-photo-picker.js';
import { CreateJobPhotos } from './create-job-photos.js';

@Component({
  selector: 'app-create-job',
  imports: [
    Navbar,
    Footer,
    RouterLink,
    ReactiveFormsModule,
    PostingStatusModal,
    TranslatePipe,
    Select,
    LucideDynamicIcon,
    JobPhotoPicker,
  ],
  providers: [CreateJobPhotos],
  templateUrl: './create-job.html',
  styleUrl: './create-job.css',
})
export class CreateJob implements OnInit {
  private readonly i18n = inject(I18nService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly jobService = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  private readonly titleCase = new TitleCasePipe();

  readonly icons = {
    back: LucideArrowLeft,
    loading: LucideLoaderCircle,
  };
  readonly skillOptions = computed(() => [
    {
      value: '',
      label: this.i18n.translate('createJob.selectSkill'),
      disabled: true,
    },
    ...this.skills().map((value) => ({
      value,
      label: this.titleCase.transform(value.replaceAll('_', ' ')),
    })),
  ]);
  readonly cityOptions = computed(() => [
    {
      value: '',
      label: this.i18n.translate('createJob.selectCity'),
      disabled: true,
    },
    ...this.cities().map((value) => ({
      value,
      label: this.titleCase.transform(value.replaceAll('_', ' ')),
    })),
  ]);
  readonly durationOptions = computed<readonly SelectOption<EstimatedDuration | ''>[]>(() => [
    {
      value: '',
      label: this.i18n.translate('createJob.selectDuration'),
      disabled: true,
    },
    {
      value: 'HALF_DAY',
      label: this.i18n.translate('createJob.halfDay'),
    },
    {
      value: 'FULL_DAY',
      label: this.i18n.translate('createJob.fullDay'),
    },
    {
      value: 'MULTI_DAY',
      label: this.i18n.translate('createJob.multipleDays'),
    },
  ]);
  readonly timeOptions = computed<readonly SelectOption<PreferredTime>[]>(() => [
    {
      value: 'ANYTIME',
      label: this.i18n.translate('createJob.anytime'),
    },
    {
      value: 'MORNING',
      label: this.i18n.translate('createJob.morning'),
    },
    {
      value: 'AFTERNOON',
      label: this.i18n.translate('createJob.afternoon'),
    },
    {
      value: 'EVENING',
      label: this.i18n.translate('createJob.evening'),
    },
  ]);

  setControl<T>(control: FormControl<T>, value: T): void {
    control.setValue(value);
    control.markAsDirty();
    control.markAsTouched();
  }

  readonly skills = signal<string[]>([]);
  readonly cities = signal<string[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly referenceError = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly createdJob = signal<JobResponse | null>(null);
  readonly showFailure = signal(false);
  readonly photoState = inject(CreateJobPhotos);
  readonly isRedirecting = signal(false);
  readonly isPosting = computed(
    () => this.isSaving() || this.photoState.isUploading() || this.isRedirecting(),
  );
  readonly formLocked = computed(() => this.isPosting() || !!this.createdJob());
  readonly referencesDisabled = computed(
    () => this.isLoading() || !!this.referenceError() || this.formLocked(),
  );
  readonly postingLabel = computed(() =>
    this.photoState.isUploading()
      ? 'createJob.uploadingPhotos'
      : this.isSaving()
        ? 'createJob.posting'
        : this.isRedirecting()
          ? 'createJob.openingJobs'
          : 'createJob.post',
  );

  private hasUnfinishedWork(): boolean {
    if (this.isRedirecting()) return false;
    return (
      this.isSaving() ||
      this.photoState.isUploading() ||
      (!this.createdJob() && (this.form.dirty || this.photoState.photos().length > 0))
    );
  }

  canLeave(): boolean {
    return (
      !this.hasUnfinishedWork() || window.confirm(this.i18n.translate('createJob.leaveConfirm'))
    );
  }

  selectPhotos(files: File[]): void {
    if (this.isPosting() || this.createdJob()) return;
    this.photoState.select(files);
  }

  removePhoto(photo: JobPhoto): void {
    if (this.isPosting() || this.createdJob()) return;
    this.photoState.remove(photo);
  }

  private async finishPosting(): Promise<void> {
    if (!this.createdJob()) return;
    if (this.photoState.uploadErrors().length) {
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
    } catch {}
    this.isRedirecting.set(false);
    this.errorMessage.set(this.i18n.translate('createJob.postedRedirectError'));
    this.showFailure.set(true);
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
    estimatedDuration: this.formBuilder.control<EstimatedDuration | null>(
      null,
      Validators.required,
    ),
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

    if (control.hasError('required')) return this.i18n.translate('createJob.requiredError');
    if (control.hasError('maxlength')) {
      return field === 'title'
        ? this.i18n.translate('createJob.titleLimit')
        : this.i18n.translate('createJob.addressLimit');
    }
    if (field === 'budget') return this.i18n.translate('createJob.budgetError');
    return this.i18n.translate('createJob.whitespaceError');
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
    this.jobService
      .createJob(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (job) => {
          this.createdJob.set(job);
          this.isSaving.set(false);
          this.photoState.upload(job.id, () => {
            void this.finishPosting();
          });
        },
        error: (error: HttpErrorResponse) => {
          this.isSaving.set(false);
          this.showFailure.set(true);
          if (error.status === 0) {
            this.errorMessage.set(this.i18n.translate('createJob.postUncertain'));
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

          this.errorMessage.set(error.error?.message ?? this.i18n.translate('createJob.postError'));
        },
      });
  }
}
