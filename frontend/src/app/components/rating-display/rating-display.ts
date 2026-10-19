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
  @Input() summary!: UserRatingSummary | null;
  @Input() showDistribution = true;
  @Input() compact = false;
  readonly Star = Star;

  get averageScore(): number {
    return this.summary?.averageScore ?? 0;
  }

  get totalRatings(): number {
    return this.summary?.totalRatings ?? 0;
  }

  get roundedAvg(): number {
    return Math.round(this.averageScore * 2) / 2;
  }
}