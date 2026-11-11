import { DOCUMENT } from '@angular/common';
import { afterNextRender, Component, DestroyRef, inject, Injector, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../services/auth.js';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiError } from '../../types/api.js';
import { authDestination } from '../../guards/auth-destination.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { TranslationKey } from '../../i18n/language.js';
import { LucideDynamicIcon, LucideEye, LucideEyeOff } from '@lucide/angular';

type Field = 'firstName' | 'lastName' | 'email' | 'password';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, LucideDynamicIcon],
  templateUrl: './register.html',
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  readonly icons = { show: LucideEye, hide: LucideEyeOff };
  readonly fields: readonly Field[] = ['firstName', 'lastName', 'email', 'password'];
  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    lastName: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.pattern(/\S/), Validators.minLength(8), Validators.maxLength(128)]],
  });
  readonly errorMessage = signal<TranslationKey | null>(null);
  readonly submitting = signal(false);
  readonly passwordVisible = signal(false);

  togglePassword(input: HTMLInputElement, event: MouseEvent): void {
    const start = input.selectionStart, end = input.selectionEnd, direction = input.selectionDirection;
    this.passwordVisible.update(visible => !visible);
    afterNextRender(() => {
      if (event.detail > 0) input.focus({ preventScroll: true });
      if (start !== null && end !== null) input.setSelectionRange(start, end, direction ?? undefined);
    }, { injector: this.injector });
  }

  fieldError(field: Field): string | null {
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) return null;
    if (control.hasError('emailTaken')) return this.i18n.translate('auth.register.emailTaken');
    if (control.hasError('server')) return this.i18n.translate('auth.register.fieldInvalid');
    if (control.hasError('required') || control.hasError('pattern')) {
      const keys = { firstName: 'auth.error.firstNameRequired', lastName: 'auth.error.lastNameRequired', email: 'auth.error.emailRequired', password: 'auth.error.passwordRequired' } as const;
      return this.i18n.translate(keys[field]);
    }
    if (control.hasError('email')) return this.i18n.translate('auth.error.emailInvalid');
    if (control.hasError('minlength')) return this.i18n.translate('auth.error.minLength', { length: control.errors['minlength'].requiredLength });
    if (control.hasError('maxlength')) return this.i18n.translate('auth.error.maxLength', { length: control.errors['maxlength'].requiredLength });
    return null;
  }

  private focusInvalid(): void {
    afterNextRender(() => {
      const field = this.fields.find(name => this.form.controls[name].invalid);
      if (field) this.document.getElementById(`register-${field}`)?.focus();
    }, { injector: this.injector });
  }

  register(): void {
    if (this.submitting()) return;
    for (const name of ['firstName', 'lastName', 'email'] as const) {
      const control = this.form.controls[name]; control.setValue(control.value.trim());
    }
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.focusInvalid(); return; }
    this.errorMessage.set(null);
    this.submitting.set(true);
    this.authService.register(this.form.getRawValue()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: async response => {
        this.authService.saveToken(response.accessToken);
        try {
          if (await this.router.navigateByUrl(authDestination(this.route.snapshot.queryParamMap.get('returnUrl')))) return;
          this.errorMessage.set('auth.register.failed');
        } catch {
          this.errorMessage.set('auth.register.failed');
        }
        this.submitting.set(false);
      },
      error: (error: HttpErrorResponse) => {
        const body = error.error as ApiError | undefined;
        this.submitting.set(false);
        if (error.status === 409 || body?.error?.code === 'EMAIL_ALREADY_IN_USE') {
          this.form.controls.email.setErrors({ emailTaken: true }); this.focusInvalid(); return;
        }
        if (body?.error?.code === 'VALIDATION_ERROR') {
          for (const failure of body.error.errors ?? []) this.form.get(failure.field)?.setErrors({ server: true });
          if (this.form.invalid) { this.focusInvalid(); return; }
        }
        this.errorMessage.set(error.status === 429 ? 'auth.register.tooManyAttempts'
          : error.status === 0 || error.status >= 500 ? 'auth.register.unavailable' : 'auth.register.failed');
      },
    });
  }
}
