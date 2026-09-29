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
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-categories',
  imports: [LucideDynamicIcon, TranslatePipe],
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

  // name stays in English because it selects the icon; label is the key shown
  // to the person, translated in the template.
  categories = signal<{ name: string; label: TranslationKey }[]>([
    { name: 'Plumbing', label: 'categories.plumbing' },
    { name: 'Carpentry', label: 'categories.carpentry' },
    { name: 'Electrical', label: 'categories.electrical' },
    { name: 'Cleaning', label: 'categories.cleaning' },
    { name: 'Painting', label: 'categories.painting' },
    { name: 'Gardening', label: 'categories.gardening' },
  ]);

  getIcon(name: string) {
    return this.iconMap[name] ?? LucideShapes;
  }
}
