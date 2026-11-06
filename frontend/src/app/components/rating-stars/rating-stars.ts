import { Component, inject, Input } from '@angular/core';
import { LucideStar } from '@lucide/angular';
import { I18nService } from '../../services/i18n.js';

/** A read-only score. Each star can be partly filled, including decimal averages. */
@Component({
  selector: 'app-rating-stars',
  imports: [LucideStar],
  template: `
    <span class="stars" role="img" [attr.aria-label]="label">
      @for (star of stars; track star) {
        <span class="star" aria-hidden="true">
          <svg lucideStar class="star-outline"></svg>
          <span class="fill" [style.width.%]="fillPercentage(star)"><svg lucideStar></svg></span>
        </span>
      }
    </span>
  `,
  styles: `
    :host { display: inline-flex; vertical-align: middle; }
    .stars { display: inline-flex; gap: .18rem; }
    .star { position: relative; display: block; width: 1.15rem; height: 1.15rem; }
    svg { width: 1.15rem; height: 1.15rem; stroke-width: 1.7; }
    .star-outline { color: #d1d5db; fill: #f3f4f6; }
    .fill { position: absolute; inset-block: 0; inset-inline-start: 0; overflow: hidden; color: #b7790b; }
    .fill svg { position: absolute; inset-inline-start: 0; fill: #f5b841; }
  `,
})
export class RatingStars {
  @Input() score = 0;
  private readonly i18n = inject(I18nService);
  readonly stars = [1, 2, 3, 4, 5];

  get label(): string {
    return this.i18n.translate('reviews.outOfFive', { score: this.score });
  }

  fillPercentage(star: number): number {
    return Math.max(0, Math.min(1, this.score - star + 1)) * 100;
  }
}
