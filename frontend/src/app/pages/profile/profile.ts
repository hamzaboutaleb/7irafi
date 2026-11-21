import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, Injector, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, merge, of, Subject, switchMap } from 'rxjs';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  LucideBell,
  LucideBriefcaseBusiness,
  LucideDynamicIcon,
  LucidePencil,
  LucideStar,
  LucideUserRound,
} from '@lucide/angular';
import { AvatarUpload } from '../../components/avatar-upload/avatar-upload.js';
import { Footer } from '../../components/footer/footer.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { NotificationPanel } from '../../components/notification-panel/notification-panel.js';
import { RatingDisplayComponent } from '../../components/rating-display/rating-display.js';
import { RatingListComponent } from '../../components/rating-list/rating-list.js';
import { fieldErrorMessage, focusFirstInvalid } from '../../forms/form-helpers.js';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { RatingService, UserRatingSummary } from '../../services/rating.js';
import { UserService } from '../../services/user.js';
import { ApiError } from '../../types/api.js';
import {
  ProfessionalOnboardingStatus,
  ProfessionalProfile,
} from '../../types/professional-profile.js';
import { UserProfile } from '../../types/user.js';

type ProfessionalSectionState = 'loading' | 'none' | 'loaded' | 'error';
type Field = 'firstName' | 'lastName';

// The tabs of the left menu.
const SECTIONS = [
  { id: 'personal', label: 'profile.personalInformation', icon: LucideUserRound },
  { id: 'professional', label: 'profile.professional', icon: LucideBriefcaseBusiness },
  { id: 'reviews', label: 'reviews.title', icon: LucideStar },
  { id: 'notifications', label: 'notifications.title', icon: LucideBell },
] as const;
type Section = (typeof SECTIONS)[number]['id'];

// The "... is required" message to show for each field.
const REQUIRED_KEYS = {
  firstName: 'auth.error.firstNameRequired',
  lastName: 'auth.error.lastNameRequired',
} as const;

// The text of the pro status pill.
const STATUS_KEYS: Record<ProfessionalOnboardingStatus, TranslationKey> = {
  INCOMPLETE: 'profile.status.incomplete',
  ACTIVE: 'profile.status.active',
  SUSPENDED: 'profile.status.suspended',
};

@Component({
  selector: 'app-profile',
  imports: [
    ReactiveFormsModule,
    Navbar,
    Footer,
    DatePipe,
    RouterLink,
    AvatarUpload,
    NotificationPanel,
    TranslatePipe,
    RatingDisplayComponent,
    RatingListComponent,
    JobDetailsModal,
    LucideDynamicIcon,
    LucidePencil,
  ],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile implements OnInit {
  // services
  private readonly formBuilder = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly ratingService = inject(RatingService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);

  // left menu tabs
  readonly sections = SECTIONS;
  readonly section = signal<Section>('personal');

  // status pill text
  readonly statusKeys = STATUS_KEYS;

  // reviews tab state
  readonly ratingSummary = signal<UserRatingSummary | null>(null);
  readonly ratingsLoading = signal(false);
  readonly ratingsError = signal(false);
  // every "get my average again" (first load, Retry) goes through here
  private readonly ratingReload$ = new Subject<void>();
  readonly reviewListState = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly reviewJobId = signal<string | null>(null);
  readonly ratingType = signal<'received' | 'written'>('received');

  // page state
  readonly profile = signal<UserProfile | null>(null);
  readonly isLoading = signal(true);
  readonly isSaving = signal(false);
  readonly errorMessage = signal<TranslationKey | null>(null);
  readonly successMessage = signal<TranslationKey | null>(null);
  readonly isEditingPersonal = signal(false);
  readonly professionalSection = signal<ProfessionalSectionState>('loading');
  readonly professionalProfile = signal<ProfessionalProfile | null>(null);

  // name edit form
  readonly fields: readonly Field[] = ['firstName', 'lastName'];
  readonly form = this.formBuilder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    lastName: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
  });

  // runs when the page opens
  ngOnInit(): void {
    this.openTabFromLink();
    this.reloadRatingOnNewReview();
    this.loadProfile();
    this.loadProfessional();
  }

  // links ending with #profile-ratings-heading open the reviews tab
  // (the router opens every page at the top, see app.config.ts)
  private openTabFromLink(): void {
    this.route.fragment.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((fragment) => {
      if (fragment === 'profile-ratings-heading') this.section.set('reviews');
    });
  }

  // load my average on first load, Retry, or when someone posts a review.
  // A new trigger cancels the request still running, so the newest answer always wins.
  private reloadRatingOnNewReview(): void {
    merge(this.ratingReload$, this.ratingService.reviewsChanged)
      .pipe(
        switchMap(() => {
          const userId = this.profile()?.id;
          if (!userId) return EMPTY; // account not loaded yet
          this.ratingsLoading.set(true);
          this.ratingsError.set(false);
          // caught inside switchMap: a failed load must not stop future reloads
          return this.ratingService.getUserRatingSummary(userId).pipe(catchError(() => of(null)));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((summary) => {
        if (summary === null) this.ratingsError.set(true);
        else this.ratingSummary.set(summary);
        this.ratingsLoading.set(false);
      });
  }

  // load my account
  private loadProfile(): void {
    this.userService
      .getCurrentUser()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.profile.set(response.data);
          this.fillForm(response.data);
          this.isLoading.set(false);
          this.loadRatingSummary();
        },
        error: () => {
          this.errorMessage.set('profile.loadError');
          this.isLoading.set(false);
        },
      });
  }

  // load my pro profile
  private loadProfessional(): void {
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
          // 404 here just means "not a pro yet", not a real error.
          if (err.status === 404 && body?.error?.code === 'PROFESSIONAL_PROFILE_NOT_FOUND') {
            this.professionalSection.set('none');
            return;
          }
          this.professionalSection.set('error');
        },
      });
  }

  // put the saved names back in the form
  private fillForm(profile: UserProfile): void {
    this.form.patchValue({ firstName: profile.firstName, lastName: profile.lastName });
  }

  // load my average rating (also the Retry buttons); the stream above does the work
  loadRatingSummary(): void {
    this.ratingReload$.next();
  }

  // Edit button
  startEditingPersonal(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.isEditingPersonal.set(true);
  }

  // Cancel button
  cancelEditingPersonal(): void {
    const profile = this.profile();
    if (profile) this.fillForm(profile);
    this.errorMessage.set(null);
    this.isEditingPersonal.set(false);
  }

  // Save button
  save(): void {
    if (this.isSaving()) return; // ignore extra clicks while we wait for the server

    // Remove spaces around the names.
    for (const name of this.fields) {
      const control = this.form.controls[name];
      control.setValue(control.value.trim());
    }

    // Show the error of every field, even the ones the user never clicked.
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      focusFirstInvalid(this.form, this.fields, 'profile', this.injector);
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
          this.fillForm(response.data);
          this.successMessage.set('profile.updated');
          this.isSaving.set(false);
          this.isEditingPersonal.set(false);
        },
        error: () => {
          this.errorMessage.set('profile.saveError');
          this.isSaving.set(false);
        },
      });
  }

  // red text under a field
  fieldError(field: Field): string | null {
    return fieldErrorMessage(this.form.controls[field], REQUIRED_KEYS[field], this.i18n);
  }
}
