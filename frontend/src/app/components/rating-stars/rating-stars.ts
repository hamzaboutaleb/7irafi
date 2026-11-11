import { Component, inject, Input } from '@angular/core';
import { LucideStar } from '@lucide/angular';
import { I18nService } from '../../services/i18n.js';

/** A read-only score. Each star can be partly filled, including decimal averages. */
@Component({
  selector: 'app-rating-stars',
  imports: [LucideStar],
  template: `
    <span class="stars inline-flex [gap:.18rem]" role="img" [attr.aria-label]="label">
      @for (star of stars; track star) {
        <span class="star relative block [width:1.15rem] [height:1.15rem]" aria-hidden="true">
          <svg lucideStar class="star-outline [width:1.15rem] [height:1.15rem] [stroke-width:1.7] text-gray-300 [fill:#f3f4f6]"></svg>
          <span class="fill absolute [inset-block:0] start-0 overflow-hidden [color:#b7790b] [&.fill_svg]:absolute [&.fill_svg]:start-0 [&.fill_svg]:[fill:#f5b841]" [style.width.%]="fillPercentage(star)"><svg class="[width:1.15rem] [height:1.15rem] [stroke-width:1.7]" lucideStar></svg></span>
        </span>
      }
    </span>
  `,
  host: { class: 'inline-flex [vertical-align:middle]' },
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
