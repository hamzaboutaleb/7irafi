import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of, throwError } from 'rxjs';
import { Footer } from '../../components/footer/footer.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { UserService } from '../../services/user.js';
import { ApiError } from '../../types/api.js';
// "as ProfessionalProfileModel": this page class is already called ProfessionalProfile.
import {
  ProfessionalProfile as ProfessionalProfileModel,
  ServiceCategory,
  ServiceCity,
} from '../../types/professional-profile.js';

// What the page shows: a spinner, the empty form, the filled form, or an error.
type ProfessionalProfileMode = 'loading' | 'create' | 'edit' | 'error';

@Component({
  selector: 'app-professional-profile',
  imports: [Footer, Navbar, ReactiveFormsModule, RouterLink, TranslatePipe],
  templateUrl: './professional-profile.html',
})
export class ProfessionalProfile implements OnInit {
  // services
  private readonly userService = inject(UserService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  // page state
  readonly mode = signal<ProfessionalProfileMode>('loading');
  readonly professionalProfile = signal<ProfessionalProfileModel | null>(null);
  readonly categories = signal<ServiceCategory[]>([]);
  readonly cities = signal<ServiceCity[]>([]);
  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  // biography + chosen services + chosen cities
  readonly form = this.formBuilder.nonNullable.group({
    biography: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(2000)]],
    categoryCodes: this.formBuilder.nonNullable.control<string[]>([], Validators.required),
    cityCodes: this.formBuilder.nonNullable.control<string[]>([], Validators.required),
  });

  // runs when the page opens
  ngOnInit(): void {
    this.loadPage();
  }

  // load the lists and my pro profile at the same time (also the Retry button)
  loadPage(): void {
    this.mode.set('loading');
    this.errorMessage.set(null);

    forkJoin({
      categories: this.userService.getServiceCategories(),
      cities: this.userService.getServiceCities(),
      profile: this.userService.getCurrentProfessionalProfile().pipe(
        catchError((err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;
          // 404 just means "not a pro yet": continue with an empty form.
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

  // a service checkbox was clicked
  toggleCategory(code: string, selected: boolean): void {
    this.updateSelection(this.categoryCodes, code, selected);
  }

  // a city checkbox was clicked
  toggleCity(code: string, selected: boolean): void {
    this.updateSelection(this.cityCodes, code, selected);
  }

  // Save button: create the pro profile the first time, update it after
  save(): void {
    const currentMode = this.mode();
    // ignore extra clicks, and clicks before the page has loaded
    if (this.isSaving() || (currentMode !== 'create' && currentMode !== 'edit')) return;

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
      next: () => {
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

  // put my saved pro profile in the form
  private applyProfile(profile: ProfessionalProfileModel): void {
    this.professionalProfile.set(profile);
    this.form.patchValue({
      biography: profile.biography,
      categoryCodes: profile.categories.map((category) => category.code),
      cityCodes: profile.cities.map((city) => city.code),
    });
  }

  // add or remove one code from a checkbox list
  private updateSelection(control: FormControl<string[]>, code: string, selected: boolean): void {
    const codes = control.value;
    if (selected && !codes.includes(code)) {
      control.setValue([...codes, code]);
    } else if (!selected) {
      control.setValue(codes.filter((current) => current !== code));
    }
    control.markAsTouched();
  }

  // the text shown when loading or saving fails
  private getErrorMessage(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 0) {
      return 'The server cannot be reached. Check your connection and try again.';
    }
    if (err.status === 401 || err.status === 403) {
      return 'Your session is no longer authorized. Please sign in again.';
    }
    if (err.status >= 500) {
      return 'The server encountered a problem. Please try again shortly.';
    }
    const body = err.error as ApiError | undefined;
    return body?.error?.message ?? fallback;
  }

  // shortcuts for the HTML
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
