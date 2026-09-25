import { inject, Pipe, PipeTransform } from '@angular/core';
import { TranslationKey } from '../i18n/language.js';
import { I18nService } from '../services/i18n.js';

/**
 * Used as {{ 'nav.profile' | translate }}.
 *
 * Deliberately impure: the pipe re-runs when the language signal changes, which
 * is what makes the whole page switch language without a reload.
 */
@Pipe({
  name: 'translate',
  pure: false,
})
export class TranslatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: TranslationKey, values?: Record<string, string | number>): string {
    return this.i18n.translate(key, values);
  }
}
