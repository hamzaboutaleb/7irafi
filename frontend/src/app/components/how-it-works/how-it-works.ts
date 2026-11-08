import { Component } from '@angular/core';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-how-it-works',
  imports: [TranslatePipe],
  templateUrl: './how-it-works.html',
})
export class HowItWorks {
  readonly steps = [
    { number: '01', title: 'landing.step1Title', body: 'landing.step1Body' },
    { number: '02', title: 'landing.step2Title', body: 'landing.step2Body' },
    { number: '03', title: 'landing.step3Title', body: 'landing.step3Body' },
  ] as const;
}
