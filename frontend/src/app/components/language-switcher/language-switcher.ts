import { Component, inject, input } from '@angular/core';
import { Language } from '../../i18n/language.js';
import { I18nService } from '../../services/i18n.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { Select } from '../select/select.js';
import { LucideGlobe } from '@lucide/angular';

/** Picks the interface language; the choice is remembered in this browser. */
@Component({
  selector: 'app-language-switcher',
  imports: [TranslatePipe, Select, LucideGlobe],
  templateUrl: './language-switcher.html',
  styleUrl: './language-switcher.css',
})
export class LanguageSwitcher {
  private readonly i18n = inject(I18nService);

  readonly controlId = input('language');
  readonly options = this.i18n.options.map(option => ({
    value: option.code,
    label: option.label,
    lang: option.code,
    direction: option.direction,
  }));
  readonly current = this.i18n.language;

  change(language: Language): void {
    this.i18n.use(language);
  }
}
