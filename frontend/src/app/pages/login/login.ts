import { DOCUMENT } from '@angular/common';
import { afterNextRender, Component, DestroyRef, inject, Injector, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideDynamicIcon, LucideEye, LucideEyeOff } from '@lucide/angular';
import { AuthService } from '../../services/auth.js';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { authDestination } from '../../guards/auth-destination.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { TranslationKey } from '../../i18n/language.js';

@Component({
  selector: 'app-login',
  imports: [LucideDynamicIcon, ReactiveFormsModule, RouterLink, TranslatePipe],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  readonly icons = { show: LucideEye, hide: LucideEyeOff };
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  readonly errorMessage = signal<TranslationKey | null>(null);
  readonly submitting = signal(false);
  readonly passwordVisible = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  togglePassword(input: HTMLInputElement, event: MouseEvent): void {
    const start = input.selectionStart,
      end = input.selectionEnd,
      direction = input.selectionDirection;
    this.passwordVisible.update((visible) => !visible);
    afterNextRender(
      () => {
        if (event.detail > 0) input.focus({ preventScroll: true });
        if (start !== null && end !== null)
          input.setSelectionRange(start, end, direction ?? undefined);
      },
      { injector: this.injector },
    );
  }

  fieldError(field: 'email' | 'password'): string | null {
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) return null;
    if (control.hasError('required'))
      return this.i18n.translate(
        field === 'email' ? 'auth.error.emailRequired' : 'auth.error.passwordRequired',
      );
    if (control.hasError('email')) return this.i18n.translate('auth.error.emailInvalid');
    if (control.hasError('maxlength'))
      return this.i18n.translate('auth.error.maxLength', {
        length: control.errors['maxlength'].requiredLength,
      });
    return null;
  }

  login(): void {
    if (this.submitting()) return; // stops double clicking

    this.form.controls.email.setValue(this.form.controls.email.value.trim()); // trim the value

    this.form.markAllAsTouched(); // if the user clicks log in without writing anything  (it shows the error message if so)

    // UX
    if (this.form.invalid) {
      // after error cursor jumps to the faild box
      afterNextRender(
        () => {
          this.document
            .getElementById(this.form.controls.email.invalid ? 'login-email' : 'login-password')
            ?.focus();
        },
        { injector: this.injector },
      );
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true); // shows "Signin in"

    const { email, password } = this.form.getRawValue();

    this.authService
      .login(email, password)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // pipe to destroy in case user leavesthe page before the response
        next: (response) => {
          this.authService.saveToken(response.accessToken);
          this.router
            .navigateByUrl(authDestination(this.route.snapshot.queryParamMap.get('returnUrl'))) // keeps it only if it points inside the site, otherwise uses /
            .finally(() => this.submitting.set(false)); // re-enable the button if the page didn't open
        },
        error: (error: HttpErrorResponse) => {
          this.errorMessage.set(this.errorKey(error));
          this.submitting.set(false);
        },
      });
  }

  // which message to show for a failed login
  private errorKey(error: HttpErrorResponse): TranslationKey {
    if (error.status === 401) return 'auth.login.invalidCredentials';
    if (error.status === 429) return 'auth.login.tooManyAttempts';
    if (error.status === 0 || error.status >= 500) return 'auth.login.unavailable';
    return 'auth.login.failed';
  }
}
