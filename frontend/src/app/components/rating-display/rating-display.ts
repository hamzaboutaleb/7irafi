import { Component, inject, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RatingStars } from '../rating-stars/rating-stars.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { UserRatingSummary } from '../../services/rating.js';

@Component({
  selector: 'app-rating-display',
  standalone: true,
  imports: [CommonModule, RatingStars, TranslatePipe],
  templateUrl: './rating-display.html',
  styleUrl: './rating-display.css',
})
export class RatingDisplayComponent {
  @Input() summary: UserRatingSummary | null = null;
  @Input() showDistribution = true;
  @Input() compact = false;
  readonly i18n = inject(I18nService);
  readonly levels = [5, 4, 3, 2, 1];

  get averageScore(): number {
    return this.summary?.averageScore ?? 0;
  }

  get totalRatings(): number {
    return this.summary?.totalRatings ?? 0;
  }

  getCount(level: number): number {
    return this.summary?.scoreDistribution?.[level] ?? 0;
  }

  getPercentage(level: number): number {
    if (!this.totalRatings) return 0;
    return (this.getCount(level) / this.totalRatings) * 100;
  }

  getAriaLabel(): string {
    if (this.totalRatings === 0) {
      return this.i18n.translate('reviews.noReviews');
    }
    return this.i18n.translate('reviews.outOfFive', { score: this.averageScore.toFixed(1) }) + ', ' + this.i18n.translate('reviews.count', { count: this.totalRatings });
  }

  getDistributionAriaLabel(): string {
    if (this.totalRatings === 0) return '';
    return this.levels.map(score => this.i18n.translate('reviews.levelCount', { score, count: this.getCount(score) })).join(', ');
  }
}
