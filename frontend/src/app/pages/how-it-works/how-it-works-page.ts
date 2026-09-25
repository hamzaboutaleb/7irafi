import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Footer } from '../../components/footer/footer';
import { HowItWorks } from '../../components/how-it-works/how-it-works';
import { Navbar } from '../../components/navbar/navbar';
import { AuthService } from '../../services/auth';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-how-it-works-page',
  imports: [Navbar, Footer, HowItWorks, RouterLink, TranslatePipe],
  templateUrl: './how-it-works-page.html',
})
export class HowItWorksPage {
  private readonly authService = inject(AuthService);

  readonly isAuthenticated = Boolean(this.authService.getToken());

  readonly professionalSteps = signal<{ number: number; title: TranslationKey; description: TranslationKey }[]>([
    {
      number: 1,
      title: 'howItWorksPage.step1.title',
      description: 'howItWorksPage.step1.body',
    },
    {
      number: 2,
      title: 'howItWorksPage.step2.title',
      description: 'howItWorksPage.step2.body',
    },
    {
      number: 3,
      title: 'howItWorksPage.step3.title',
      description: 'howItWorksPage.step3.body',
    },
    {
      number: 4,
      title: 'howItWorksPage.step4.title',
      description: 'howItWorksPage.step4.body',
    },
  ]);

  readonly answers = signal<{ question: TranslationKey; answer: TranslationKey }[]>([
    {
      question: 'howItWorksPage.q1',
      answer: 'howItWorksPage.a1',
    },
    {
      question: 'howItWorksPage.q2',
      answer: 'howItWorksPage.a2',
    },
    {
      question: 'howItWorksPage.q3',
      answer: 'howItWorksPage.a3',
    },
    {
      question: 'howItWorksPage.q4',
      answer: 'howItWorksPage.a4',
    },
  ]);
}
