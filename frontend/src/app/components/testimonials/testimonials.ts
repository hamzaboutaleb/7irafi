import { Component, signal } from '@angular/core';
import { LucideDynamicIcon, LucideStar } from '@lucide/angular';

@Component({
  selector: 'app-testimonials',
  imports: [LucideDynamicIcon],
  templateUrl: './testimonials.html',
  styleUrl: './testimonials.css',
})
export class Testimonials {
  StarIcon = LucideStar;

  getStars(rating: number): number[] {
    return Array.from({ length: rating });
  }

  testimonials = signal([
    {
      name: 'Sarah M.',
      role: 'Client',
      text: 'Found a great plumber in minutes. The whole process was smooth and professional.',
      rating: 5,
    },
    {
      name: 'Karim B.',
      role: 'Plumber',
      text: 'I get new clients every week through 7irafy. Best decision I made for my business.',
      rating: 5,
    },
    {
      name: 'Fatima Z.',
      role: 'Client',
      text: 'Posted my request and had 3 professionals respond within the hour. Impressive!',
      rating: 4,
    },
  ]);
}
