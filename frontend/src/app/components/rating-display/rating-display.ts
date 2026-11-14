import { DecimalPipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';

import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { UserRatingSummary } from '../../services/rating.js';
import { RatingStars } from '../rating-stars/rating-stars.js';

/** Shows a user's average score, review count, and optional breakdown from five stars to one. */
@Component({
  selector: 'app-rating-display',
  imports: [DecimalPipe, RatingStars, TranslatePipe],
  templateUrl: './rating-display.html',
  styleUrl: './rating-display.css',
})
export class RatingDisplayComponent {
  readonly summary = input<UserRatingSummary | null>(null);
  readonly showDistribution = input(true);
  readonly compact = input(false);

  private readonly i18n = inject(I18nService);

  readonly scoreLevels = [5, 4, 3, 2, 1];

  averageScore(): number {
    return this.summary()?.averageScore ?? 0;
  }

  totalRatings(): number {
    return this.summary()?.totalRatings ?? 0;
  }

  reviewCountForScore(score: number): number {
    return this.summary()?.scoreDistribution?.[score] ?? 0;
  }

  reviewPercentageForScore(score: number): number {
    const totalRatings = this.totalRatings();
    if (!totalRatings) return 0;

    const reviewCount = this.reviewCountForScore(score);

    return (reviewCount / totalRatings) * 100;
  }

  summaryLabel(): string {
    const totalRatings = this.totalRatings();
    if (totalRatings === 0) {
      return this.i18n.translate('reviews.noReviews');
    }

    const formattedScore = this.averageScore().toFixed(1);
    const scoreLabel = this.i18n.translate('reviews.outOfFive', { score: formattedScore });
    const countLabel = this.i18n.translate('reviews.count', { count: totalRatings });

    return `${scoreLabel}, ${countLabel}`;
  }

  distributionLabel(): string {
    if (this.totalRatings() === 0) return '';

    const scoreLabels = this.scoreLevels.map((score) => {
      const count = this.reviewCountForScore(score);

      return this.i18n.translate('reviews.levelCount', { score, count });
    });

    return scoreLabels.join(', ');
  }
}
