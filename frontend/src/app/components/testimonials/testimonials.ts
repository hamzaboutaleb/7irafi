import { Component, signal } from '@angular/core';
import { LucideDynamicIcon, LucideStar } from '@lucide/angular';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-testimonials',
  imports: [LucideDynamicIcon, TranslatePipe],
  templateUrl: './testimonials.html',
})
export class Testimonials {
  StarIcon = LucideStar;

  getStars(rating: number): number[] {
    return Array.from({ length: rating });
  }

  testimonials = signal<{ name: string; role: TranslationKey; text: TranslationKey; rating: number }[]>([
    {
      name: 'Sarah M.',
      role: 'testimonials.role.client',
      text: 'testimonials.quote1',
      rating: 5,
    },
    {
      name: 'Karim B.',
      role: 'testimonials.role.plumber',
      text: 'testimonials.quote2',
      rating: 5,
    },
    {
      name: 'Fatima Z.',
      role: 'testimonials.role.client',
      text: 'testimonials.quote3',
      rating: 4,
    },
  ]);
}
