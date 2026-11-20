import { Component, DestroyRef, inject, Injector, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth.js';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { ApiError } from '../../types/api.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { TranslationKey } from '../../i18n/language.js';
import { LucideDynamicIcon, LucideEye, LucideEyeOff } from '@lucide/angular';
import { fieldErrorMessage, focusFirstInvalid } from '../../forms/form-helpers.js';

type Field = 'firstName' | 'lastName' | 'email' | 'password';

// The "... is required" message to show for each field.
const REQUIRED_KEYS = {
  firstName: 'auth.error.firstNameRequired',
  lastName: 'auth.error.lastNameRequired',
  email: 'auth.error.emailRequired',
  password: 'auth.error.passwordRequired',
} as const;

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, LucideDynamicIcon],
  templateUrl: './register.html',
  styleUrl: '../auth-page.css',
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly i18n = inject(I18nService);
  private readonly injector = inject(Injector);
  readonly icons = { show: LucideEye, hide: LucideEyeOff };
  readonly fields: readonly Field[] = ['firstName', 'lastName', 'email', 'password'];

  // nonNullable: the values are always strings, never null.
  // Without it we would need null checks before .trim() and before sending the form.
  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    lastName: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: [
      '',
      [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.minLength(8),
        Validators.maxLength(128),
      ],
    ],
  });

  readonly errorMessage = signal<TranslationKey | null>(null);
  readonly submitting = signal(false);
  readonly passwordVisible = signal(false);

  fieldError(field: Field): string | null {
    return fieldErrorMessage(this.form.controls[field], REQUIRED_KEYS[field], this.i18n);
  }

  private focusInvalid(): void {
    focusFirstInvalid(this.form, this.fields, 'register', this.injector);
  }

  register(): void {
    if (this.submitting()) return; // ignore extra clicks while we wait for the server

    // Remove spaces around the names and email.
    // We don't trim the password: a space can be part of it.
    for (const name of ['firstName', 'lastName', 'email'] as const) {
      const control = this.form.controls[name];
      control.setValue(control.value.trim());
    }

    // Show the error of every field, even the ones the user never clicked.
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.focusInvalid();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);

    // OLD (async inside next):
    // this.authService
    //   .register(this.form.getRawValue())
    //   .pipe(takeUntilDestroyed(this.destroyRef))
    //   .subscribe({
    //     next: async (response) => {
    //       const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    //       if (await this.authService.finishSignIn(response.accessToken, returnUrl)) return;
    //       this.errorMessage.set('auth.register.failed');
    //       this.submitting.set(false);
    //     },
    //     error: (error: HttpErrorResponse) => {
    //       this.submitting.set(false);
    //       if (this.showFieldErrors(error)) return;
    //       this.errorMessage.set(this.errorKey(error));
    //     },
    //   });

    // NEW (switchMap):
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');

    this.authService
      .register(this.form.getRawValue())
      .pipe(
        // account created: save the token and open the next page.
        switchMap((response) => this.authService.finishSignIn(response, returnUrl)),
        // If the user leaves the page before this finishes, ignore the rest.
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (opened) => {
          if (opened) return; // we're on the next page
          this.errorMessage.set('auth.register.failed');
          this.submitting.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          if (this.showFieldErrors(error)) return;
          this.errorMessage.set(this.errorKey(error));
        },
      });
  }

  // Some server errors belong to one field, like "email already used".
  // We show those under the field. Returns true if we did.
  private showFieldErrors(error: HttpErrorResponse): boolean {
    const body = error.error as ApiError | undefined;

    if (error.status === 409 || body?.error?.code === 'EMAIL_ALREADY_IN_USE') {
      this.form.controls.email.setErrors({ emailTaken: true });
      this.focusInvalid();
      return true;
    }

    if (body?.error?.code === 'VALIDATION_ERROR') {
      for (const failure of body.error.errors ?? []) {
        this.form.get(failure.field)?.setErrors({ server: true });
      }
      if (this.form.invalid) {
        this.focusInvalid();
        return true;
      }
    }

    return false;
  }

  // Picks the error message shown above the form for every other kind of failure.
  private errorKey(error: HttpErrorResponse): TranslationKey {
    if (error.status === 429) return 'auth.register.tooManyAttempts';
    if (error.status === 0 || error.status >= 500) return 'auth.register.unavailable';
    return 'auth.register.failed';
  }
}
