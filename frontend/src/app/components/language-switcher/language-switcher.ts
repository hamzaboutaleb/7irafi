import { Component, inject, input } from '@angular/core';
import { Language } from '../../i18n/language.js';
import { I18nService } from '../../services/i18n.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

/** Picks the interface language; the choice is remembered in this browser. */
@Component({
  selector: 'app-language-switcher',
  imports: [TranslatePipe],
  templateUrl: './language-switcher.html',
})
export class LanguageSwitcher {
  private readonly i18n = inject(I18nService);

  readonly controlId = input('language');
  readonly options = this.i18n.options;
  readonly current = this.i18n.language;

  change(event: Event): void {
    this.i18n.use((event.target as HTMLSelectElement).value as Language);
  }
}
