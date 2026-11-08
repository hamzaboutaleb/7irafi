import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '../../services/user.js';
import { UserProfile } from '../../types/user.js';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../../types/api.js';

import { AvatarUpload } from '../../components/avatar-upload/avatar-upload.js';
import { NotificationPanel } from '../../components/notification-panel/notification-panel.js';
import { Navbar } from '../../components/navbar/navbar';
import { Footer } from '../../components/footer/footer';
import { DatePipe } from '@angular/common';
import { ProfessionalProfile as ProfessionalProfileModel, ProfessionalOnboardingStatus } from '../../types/professional-profile.js';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { I18nService } from '../../services/i18n.js';
import { TranslationKey } from '../../i18n/language.js';
import { LucideDynamicIcon, LucidePencil, LucideUserRound, LucideBriefcaseBusiness, LucideStar, LucideBell } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { RatingDisplayComponent } from '../../components/rating-display/rating-display.js';
import { RatingListComponent } from '../../components/rating-list/rating-list.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { RatingService, UserRatingSummary } from '../../services/rating.js';

type ProfessionalSectionState = 'loading' | 'none' | 'loaded' | 'error';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, Navbar, Footer, DatePipe, RouterLink, AvatarUpload, NotificationPanel, TranslatePipe, RatingDisplayComponent, RatingListComponent, JobDetailsModal, LucideDynamicIcon, LucidePencil],
  templateUrl: './profile.html',
})
export class Profile implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly ratingService = inject(RatingService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);

  readonly section = signal<'personal' | 'professional' | 'reviews' | 'notifications'>('personal');
  readonly sections = [
    { id: 'personal', label: 'profile.personalInformation', icon: LucideUserRound },
    { id: 'professional', label: 'profile.professional', icon: LucideBriefcaseBusiness },
    { id: 'reviews', label: 'reviews.title', icon: LucideStar },
    { id: 'notifications', label: 'notifications.title', icon: LucideBell },
  ] as const;

  professionalStatus(status: ProfessionalOnboardingStatus): TranslationKey {
    const labels: Record<ProfessionalOnboardingStatus, TranslationKey> = {
      INCOMPLETE: 'profile.status.incomplete',
      ACTIVE: 'profile.status.active',
      SUSPENDED: 'profile.status.suspended',
    };
    return labels[status];
  }

  readonly ratingSummary = signal<UserRatingSummary | null>(null);
  readonly ratingsLoading = signal(false);
  readonly ratingsError = signal<string | null>(null);
  readonly reviewListState = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly reviewJobId = signal<string | null>(null);
  readonly ratingType = signal<'received' | 'given'>('received');

  readonly profile = signal<UserProfile | null>(null);
  readonly isLoading = signal(true);
  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly isEditingPersonal = signal(false);
  readonly professionalSection = signal<ProfessionalSectionState>('loading');
  readonly professionalProfile = signal<ProfessionalProfileModel | null>(null);
  readonly form = this.formBuilder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(50), Validators.minLength(3)]],
    lastName: ['', [Validators.required, Validators.maxLength(50), Validators.minLength(3)]],
  });

  ngOnInit(): void {
    this.route.fragment.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(fragment => {
      if (fragment === 'profile-ratings-heading') this.section.set('reviews');
    });
    this.ratingService.reviewsChanged.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadRatingSummary());
    this.userService
      .getCurrentUser()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.profile.set(response.data);
          this.loadRatingSummary();
          this.form.patchValue({
            firstName: response.data.firstName,
            lastName: response.data.lastName,
          });
          this.isLoading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;

          this.errorMessage.set(body?.error?.message ?? this.i18n.translate('profile.loadError'));
          this.isLoading.set(false);
        },
      });
    this.userService
      .getCurrentProfessionalProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.professionalProfile.set(response.data);
          this.professionalSection.set('loaded');
        },
        error: (err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;

          if (err.status === 404 && body?.error?.code === 'PROFESSIONAL_PROFILE_NOT_FOUND') {
            this.professionalProfile.set(null);
            this.professionalSection.set('none');
            return;
          }

          this.professionalSection.set('error');
        },
      });
  }

  startEditingPersonal(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.isEditingPersonal.set(true);
  }

  loadRatingSummary(): void {
    const userId = this.profile()?.id;
    if (!userId || this.ratingsLoading()) return;
    this.ratingsLoading.set(true);
    this.ratingsError.set(null);
    this.ratingService.getSummary(userId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: summary => {
        this.ratingSummary.set(summary);
        this.ratingsLoading.set(false);
      },
      error: () => {
        this.ratingsError.set('Unable to load your rating summary. Please try again.');
        this.ratingsLoading.set(false);
      },
    });
  }

  cancelEditingPersonal(): void {
    const currentProfile = this.profile();

    if (currentProfile) {
      this.form.patchValue({
        firstName: currentProfile.firstName,
        lastName: currentProfile.lastName,
      });
    }

    this.errorMessage.set(null);
    this.isEditingPersonal.set(false);
  }

  save(): void {
    if (this.isSaving()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.isSaving.set(true);

    this.userService
      .updateCurrentUser(this.form.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.profile.set(response.data);
          this.form.patchValue({
            firstName: response.data.firstName,
            lastName: response.data.lastName,
          });
          this.successMessage.set(this.i18n.translate('profile.updated'));
          this.isSaving.set(false);
          this.isEditingPersonal.set(false);
        },
        error: (err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;

          this.errorMessage.set(body?.error?.message ?? this.i18n.translate('profile.saveError'));
          this.isSaving.set(false);
        },
      });
  }

  get firstName() {
    return this.form.controls.firstName;
  }
  get lastName() {
    return this.form.controls.lastName;
  }
}
