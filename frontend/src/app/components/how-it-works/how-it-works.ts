import { Component, signal } from '@angular/core';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-how-it-works',
  imports: [TranslatePipe],
  templateUrl: './how-it-works.html',
  styleUrl: './how-it-works.css',
})
export class HowItWorks {
  steps = signal<{ number: number; title: TranslationKey; description: TranslationKey }[]>([
    { number: 1, title: 'howItWorks.step1.title', description: 'howItWorks.step1.body' },
    { number: 2, title: 'howItWorks.step2.title', description: 'howItWorks.step2.body' },
    { number: 3, title: 'howItWorks.step3.title', description: 'howItWorks.step3.body' },
  ]);
}
