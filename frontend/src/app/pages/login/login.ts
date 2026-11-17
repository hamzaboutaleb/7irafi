import { Component, DestroyRef, inject, Injector, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth.js';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { TranslationKey } from '../../i18n/language.js';
import { LucideDynamicIcon, LucideEye, LucideEyeOff } from '@lucide/angular';
import { fieldErrorMessage, focusFirstInvalid } from '../../forms/form-helpers.js';

type Field = 'email' | 'password';

// The "... is required" message to show for each field.
const REQUIRED_KEYS = {
  email: 'auth.error.emailRequired',
  password: 'auth.error.passwordRequired',
} as const;

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, LucideDynamicIcon],
  templateUrl: './login.html',
  styleUrl: '../auth-page.css',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly i18n = inject(I18nService);
  private readonly injector = inject(Injector);
  readonly icons = { show: LucideEye, hide: LucideEyeOff };
  readonly fields: readonly Field[] = ['email', 'password'];

  // nonNullable: the values are always strings, never null.
  // Without it we would need null checks before .trim() and before sending the form.
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  readonly errorMessage = signal<TranslationKey | null>(null);
  readonly submitting = signal(false);
  readonly passwordVisible = signal(false);

  fieldError(field: Field): string | null {
    return fieldErrorMessage(this.form.controls[field], REQUIRED_KEYS[field], this.i18n);
  }

  private focusInvalid(): void {
    focusFirstInvalid(this.form, this.fields, 'login', this.injector);
  }

  login(): void {
    if (this.submitting()) return; // ignore extra clicks while we wait for the server

    // Remove spaces around the email. We don't trim the password: a space can be part of it.
    this.form.controls.email.setValue(this.form.controls.email.value.trim());

    // Show the error of every field, even the ones the user never clicked.
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.focusInvalid();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true); // the button text changes to "Signing in…"

    const { email, password } = this.form.getRawValue();
 
    // OLD (async inside next):
    // this.authService
    //   .login(email, password)
    //   // If the user leaves the page before the server answers, ignore the answer.
    //   .pipe(takeUntilDestroyed(this.destroyRef))
    //   .subscribe({
    //     next: async (response) => {
    //       const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    //       if (await this.authService.finishSignIn(response.accessToken, returnUrl)) return;
    //       this.errorMessage.set('auth.login.failed');
    //       this.submitting.set(false);
    //     },
    //     error: (error: HttpErrorResponse) => {
    //       this.submitting.set(false);
    //       this.errorMessage.set(this.errorKey(error));
    //     },
    //   });

    // NEW (switchMap):
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');

    this.authService
      .login(email, password)
      .pipe(
        // login answered: save the token and open the next page.
        switchMap((response) => this.authService.finishSignIn(response.accessToken, returnUrl)),
        // If the user leaves the page before this finishes, ignore the rest.
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (opened) => {
          if (opened) return; // we're on the next page
          this.errorMessage.set('auth.login.failed');
          this.submitting.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          this.errorMessage.set(this.errorKey(error));
        },
      });
  }

  // Picks the error message shown above the form when login fails.
  private errorKey(error: HttpErrorResponse): TranslationKey {
    if (error.status === 401) return 'auth.login.invalidCredentials';
    if (error.status === 429) return 'auth.login.tooManyAttempts';
    if (error.status === 0 || error.status >= 500) return 'auth.login.unavailable';
    return 'auth.login.failed';
  }
}
