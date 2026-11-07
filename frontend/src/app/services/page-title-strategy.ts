import { effect, inject, Injectable, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { I18nService } from './i18n.js';
import { TranslationKey } from '../i18n/language.js';

@Injectable()
export class PageTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly i18n = inject(I18nService);
  private readonly pageKey = signal<TranslationKey | undefined>(undefined);

  constructor() {
    super();
    effect(() => {
      const key = this.pageKey();
      this.title.setTitle(key ? `${this.i18n.translate(key)} · 7irafy` : '7irafy');
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.pageKey.set(this.buildTitle(snapshot) as TranslationKey | undefined);
  }
}
