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
import { ApiError } from '../../types/api.js';
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
    const start = input.selectionStart, end = input.selectionEnd, direction = input.selectionDirection;
    this.passwordVisible.update(visible => !visible);
    afterNextRender(() => {
      if (event.detail > 0) input.focus({ preventScroll: true });
      if (start !== null && end !== null) input.setSelectionRange(start, end, direction ?? undefined);
    }, { injector: this.injector });
  }

  fieldError(field: 'email' | 'password'): string | null {
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) return null;
    if (control.hasError('required')) return this.i18n.translate(field === 'email' ? 'auth.error.emailRequired' : 'auth.error.passwordRequired');
    if (control.hasError('email')) return this.i18n.translate('auth.error.emailInvalid');
    if (control.hasError('maxlength')) return this.i18n.translate('auth.error.maxLength', { length: control.errors['maxlength'].requiredLength });
    return null;
  }

  login(): void {
    if (this.submitting()) return;
    this.form.controls.email.setValue(this.form.controls.email.value.trim());
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      afterNextRender(() => {
        this.document.getElementById(this.form.controls.email.invalid ? 'login-email' : 'login-password')?.focus();
      }, { injector: this.injector });
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);
    const { email, password } = this.form.getRawValue();
    this.authService.login(email, password).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: async response => {
        this.authService.saveToken(response.accessToken);
        try {
          if (await this.router.navigateByUrl(authDestination(this.route.snapshot.queryParamMap.get('returnUrl')))) return;
          this.errorMessage.set('auth.login.failed');
        } catch {
          this.errorMessage.set('auth.login.failed');
        }
        this.submitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        const body = error.error as ApiError | undefined;
        this.errorMessage.set(error.status === 401 || body?.error?.code === 'INVALID_CREDENTIALS'
          ? 'auth.login.invalidCredentials'
          : error.status === 429 ? 'auth.login.tooManyAttempts'
          : error.status === 0 || error.status >= 500 ? 'auth.login.unavailable'
          : 'auth.login.failed');
        this.submitting.set(false);
      },
    });
  }
}
