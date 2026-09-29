import { DOCUMENT } from '@angular/common';
import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { ar } from '../i18n/ar.js';
import { en } from '../i18n/en.js';
import { fr } from '../i18n/fr.js';
import { Language, LANGUAGE_OPTIONS, LANGUAGES, TranslationKey } from '../i18n/language.js';

const STORAGE_KEY = 'language';

const DICTIONARIES: Record<Language, Record<TranslationKey, string>> = {
  en,
  fr,
  ar,
};

/**
 * The chosen language, and the strings that go with it.
 *
 * Translation happens at runtime rather than at build time, because the point
 * is switching language in place: Angular's compile-time i18n would need one
 * build and one URL per language.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);

  private readonly current = signal<Language>(this.initialLanguage());
  readonly language = this.current.asReadonly();
  readonly options = LANGUAGE_OPTIONS;

  readonly direction = computed(
    () => LANGUAGE_OPTIONS.find((option) => option.code === this.current())?.direction ?? 'ltr');

  constructor() {
    // Keeps <html lang> and <html dir> in step with the choice. dir is what
    // makes Arabic lay out right to left; Tailwind's logical properties follow it.
    effect(() => {
      const element = this.document.documentElement;
      element.lang = this.current();
      element.dir = this.direction();
    });
  }

  use(language: Language): void {
    this.current.set(language);

    try {
      localStorage.setItem(STORAGE_KEY, language);
    } catch {
      // A blocked storage only costs the choice on the next visit.
    }
  }

  /**
   * The string for a key, with {placeholders} filled in. An unknown key returns
   * the key itself, which is visible in the UI rather than silently empty.
   */
  translate(key: TranslationKey, values?: Record<string, string | number>): string {
    const text = DICTIONARIES[this.current()][key] ?? DICTIONARIES.en[key] ?? key;

    if (!values) return text;

    return Object.entries(values).reduce(
      (result, [name, value]) => result.replaceAll(`{${name}}`, String(value)),
      text);
  }

  private initialLanguage(): Language {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved && (LANGUAGES as readonly string[]).includes(saved)) {
        return saved as Language;
      }
    } catch {
      // Ignored: fall through to the browser's own preference.
    }

    const browser = this.document.defaultView?.navigator.language.slice(0, 2);
    return (LANGUAGES as readonly string[]).includes(browser ?? '') ? (browser as Language) : 'en';
  }
}
