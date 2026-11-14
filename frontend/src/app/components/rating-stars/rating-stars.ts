import { Component, inject, input } from '@angular/core';
import { LucideStar } from '@lucide/angular';
import { I18nService } from '../../services/i18n.js';

/** A read-only score. Each star can be partly filled, including decimal averages. */
@Component({
  selector: 'app-rating-stars',
  imports: [LucideStar],
  templateUrl: './rating-stars.html',
  styleUrl: './rating-stars.css',
})
export class RatingStars {
  readonly score = input(0);

  private readonly i18n = inject(I18nService);

  readonly starNumbers = [1, 2, 3, 4, 5];

  ratingLabel(): string {
    return this.i18n.translate('reviews.outOfFive', { score: this.score() });
  }

  starFillPercentage(starNumber: number): number {
    // With a score of 3.5, the fourth star has 0.5 points left to display.
    const scoreBeforeStar = starNumber - 1;
    const remainingScore = this.score() - scoreBeforeStar;

    if (remainingScore <= 0) return 0;
    if (remainingScore >= 1) return 100;

    return remainingScore * 100;
  }
}
