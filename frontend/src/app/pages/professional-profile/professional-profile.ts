import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { catchError, forkJoin, of, throwError } from 'rxjs';

import { Footer } from '../../components/footer/footer';
import { Navbar } from '../../components/navbar/navbar';
import { UserService } from '../../services/user.js';
import { ApiError } from '../../types/api.js';
import {
  ProfessionalProfile as ProfessionalProfileModel,
  ServiceCategory,
  ServiceCity,
} from '../../types/professional-profile.js';

type ProfessionalProfileMode = 'loading' | 'create' | 'edit' | 'error';

@Component({
  selector: 'app-professional-profile',
  imports: [Footer, Navbar, ReactiveFormsModule, RouterLink],
  templateUrl: './professional-profile.html',
  styleUrl: './professional-profile.css',
})
export class ProfessionalProfile implements OnInit {
  private readonly userService = inject(UserService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  readonly mode = signal<ProfessionalProfileMode>('loading');
  readonly professionalProfile = signal<ProfessionalProfileModel | null>(null);
  readonly categories = signal<ServiceCategory[]>([]);
  readonly cities = signal<ServiceCity[]>([]);
  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    biography: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(2000)]],
    categoryCodes: this.formBuilder.nonNullable.control<string[]>([], Validators.required),
    cityCodes: this.formBuilder.nonNullable.control<string[]>([], Validators.required),
  });

  ngOnInit(): void {
    this.loadPage();
  }

  loadPage(): void {
    this.mode.set('loading');
    this.errorMessage.set(null);

    forkJoin({
      categories: this.userService.getServiceCategories(),
      cities: this.userService.getServiceCities(),
      profile: this.userService.getCurrentProfessionalProfile().pipe(
        catchError((err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;

          if (err.status === 404 && body?.error?.code === 'PROFESSIONAL_PROFILE_NOT_FOUND') {
            return of(null);
          }

          return throwError(() => err);
        }),
      ),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ categories, cities, profile }) => {
          this.categories.set(categories.data);
          this.cities.set(cities.data);

          if (profile === null) {
            this.professionalProfile.set(null);
            this.form.reset({ biography: '', categoryCodes: [], cityCodes: [] });
            this.mode.set('create');
            return;
          }

          this.applyProfile(profile.data);
          this.mode.set('edit');
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage.set(
            this.getErrorMessage(err, 'Unable to load professional-profile information.'),
          );
          this.mode.set('error');
        },
      });
  }

  toggleCategory(code: string, selected: boolean): void {
    this.updateSelection(this.categoryCodes, code, selected);
  }

  toggleCity(code: string, selected: boolean): void {
    this.updateSelection(this.cityCodes, code, selected);
  }

  save(): void {
    const currentMode = this.mode();

    if (this.isSaving() || (currentMode !== 'create' && currentMode !== 'edit')) {
      return;
    }

    this.biography.setValue(this.biography.value.trim());

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.isSaving.set(true);

    const request = this.form.getRawValue();
    const operation =
      currentMode === 'create'
        ? this.userService.createProfessionalProfile(request)
        : this.userService.updateProfessionalProfile(request);

    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.router.navigate(['/profile']);
        this.isSaving.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(
          this.getErrorMessage(err, 'Unable to save your professional profile.'),
        );
        this.isSaving.set(false);
      },
    });
  }

  private applyProfile(profile: ProfessionalProfileModel): void {
    this.professionalProfile.set(profile);
    this.form.patchValue({
      biography: profile.biography,
      categoryCodes: profile.categories.map((category) => category.code),
      cityCodes: profile.cities.map((city) => city.code),
    });
  }

  private updateSelection(
    control: typeof this.categoryCodes,
    code: string,
    selected: boolean,
  ): void {
    const currentCodes = control.value;
    const nextCodes = selected
      ? Array.from(new Set([...currentCodes, code]))
      : currentCodes.filter((currentCode) => currentCode !== code);

    control.setValue(nextCodes);
    control.markAsTouched();
  }

  private getErrorMessage(err: HttpErrorResponse, fallback: string): string {
    const body = err.error as ApiError | undefined;

    if (err.status === 0) {
      return 'The server cannot be reached. Check your connection and try again.';
    }

    if (err.status === 401 || err.status === 403) {
      return 'Your session is no longer authorized. Please sign in again.';
    }

    if (err.status >= 500) {
      return 'The server encountered a problem. Please try again shortly.';
    }

    return body?.error?.message ?? fallback;
  }

  get biography() {
    return this.form.controls.biography;
  }

  get categoryCodes() {
    return this.form.controls.categoryCodes;
  }

  get cityCodes() {
    return this.form.controls.cityCodes;
  }
}
