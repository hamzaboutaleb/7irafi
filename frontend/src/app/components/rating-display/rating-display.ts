import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Star } from '@lucide/angular';
import { UserRatingSummary } from '../../services/rating.js';

@Component({
  selector: 'app-rating-display',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './rating-display.html',
  styleUrl: './rating-display.css',
})
export class RatingDisplayComponent {
  @Input() summary: UserRatingSummary | null = null;
  @Input() showDistribution = true;
  @Input() compact = false;
  readonly Star = Star;
  readonly levels = [5, 4, 3, 2, 1];

  get averageScore(): number {
    return this.summary?.averageScore ?? 0;
  }

  get totalRatings(): number {
    return this.summary?.totalRatings ?? 0;
  }

  get roundedAvg(): number {
    return Math.round(this.averageScore * 2) / 2;
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
      return 'No ratings yet';
    }
    return `Average rating ${this.averageScore.toFixed(1)} out of 5, based on ${this.totalRatings} reviews`;
  }

  getDistributionAriaLabel(): string {
    if (this.totalRatings === 0) return '';
    const parts = this.levels.map(l => `${this.getCount(l)} ${l}-star`);
    return `Rating breakdown: ${parts.join(', ')}`;
  }
}