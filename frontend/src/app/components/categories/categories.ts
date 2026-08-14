import { Component, signal } from '@angular/core';
import {
  LucideDynamicIcon,
  LucideWrench,
  LucideHammer,
  LucideZap,
  LucideSparkles,
  LucidePaintBucket,
  LucideTreePine,
  LucideShapes,
} from '@lucide/angular';

@Component({
  selector: 'app-categories',
  imports: [LucideDynamicIcon],
  templateUrl: './categories.html',
  styleUrl: './categories.css',
})
export class Categories {
  private iconMap: Record<string, any> = {
    Plumbing: LucideWrench,
    Carpentry: LucideHammer,
    Electrical: LucideZap,
    Cleaning: LucideSparkles,
    Painting: LucidePaintBucket,
    Gardening: LucideTreePine,
  };

  categories = signal([
    { name: 'Plumbing' },
    { name: 'Carpentry' },
    { name: 'Electrical' },
    { name: 'Cleaning' },
    { name: 'Painting' },
    { name: 'Gardening' },
  ]);

  getIcon(name: string) {
    return this.iconMap[name] ?? LucideShapes;
  }
}
