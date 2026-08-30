import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '../../services/user.js';
import { UserProfile } from '../../types/user.js';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../../types/api.js';

import { Navbar } from '../../components/navbar/navbar';
import { Footer } from '../../components/footer/footer';
import { DatePipe } from '@angular/common';
import { ProfessionalProfile as ProfessionalProfileModel } from '../../types/professional-profile.js';
import { RouterLink } from "@angular/router";

type ProfessionalSectionState = 'loading' | 'none' | 'loaded' | 'error';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, Navbar, Footer, DatePipe, RouterLink],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly destroyRef = inject(DestroyRef);

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
    this.userService
      .getCurrentUser()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.profile.set(response.data);
          this.form.patchValue({
            firstName: response.data.firstName,
            lastName: response.data.lastName,
          });
          this.isLoading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;

          this.errorMessage.set(body?.error?.message ?? 'Unable to load your profile.');
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
          this.successMessage.set('Profile updated successfully.');
          this.isSaving.set(false);
          this.isEditingPersonal.set(false);
        },
        error: (err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;

          this.errorMessage.set(body?.error?.message ?? 'Unable to update your profile.');
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
