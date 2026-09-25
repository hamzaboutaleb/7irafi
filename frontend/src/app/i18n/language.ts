import { en } from './en.js';

export const LANGUAGES = ['en', 'fr', 'ar'] as const;

export type Language = (typeof LANGUAGES)[number];

export interface LanguageOption {
  code: Language;
  /** The language's own name, which is what a speaker of it recognises. */
  label: string;
  direction: 'ltr' | 'rtl';
}

export const LANGUAGE_OPTIONS: LanguageOption[] = [
  { code: 'en', label: 'English', direction: 'ltr' },
  { code: 'fr', label: 'Français', direction: 'ltr' },
  { code: 'ar', label: 'العربية', direction: 'rtl' },
];

/** Every translatable string in the app, flat and typed. */
export type TranslationKey = keyof typeof en;
