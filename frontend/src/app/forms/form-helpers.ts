import { DOCUMENT } from '@angular/common';
import { afterNextRender, Injector } from '@angular/core';
import { AbstractControl, FormGroup } from '@angular/forms';
import { TranslationKey } from '../i18n/language.js';
import { I18nService } from '../services/i18n.js';

// Returns the red error text for one field (most useful error first), or null.
export function fieldErrorMessage(
  control: AbstractControl,
  requiredKey: TranslationKey,
  i18n: I18nService,
): string | null {
  if (!control.touched || !control.errors) return null;

  // These two come from the server, not from the form (only register uses them).
  if (control.hasError('emailTaken')) return i18n.translate('auth.register.emailTaken');
  if (control.hasError('server')) return i18n.translate('auth.register.fieldInvalid');

  // "pattern" means the password is only spaces, so we treat it like an empty field.
  if (control.hasError('required') || control.hasError('pattern'))
    return i18n.translate(requiredKey);
  if (control.hasError('email')) return i18n.translate('auth.error.emailInvalid');
  if (control.hasError('minlength'))
    return i18n.translate('auth.error.minLength', {
      length: control.errors['minlength'].requiredLength,
    });
  if (control.hasError('maxlength'))
    return i18n.translate('auth.error.maxLength', {
      length: control.errors['maxlength'].requiredLength,
    });
  return null;
}

// Moves the cursor into the first field with an error (ids like "login-email").
export function focusFirstInvalid(
  form: FormGroup,
  fields: readonly string[],
  idPrefix: string,
  injector: Injector,
): void {
  afterNextRender(
    () => {
      const field = fields.find((name) => form.get(name)?.invalid);
      if (field) injector.get(DOCUMENT).getElementById(`${idPrefix}-${field}`)?.focus();
    },
    { injector },
  );
}
