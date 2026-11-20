import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideDynamicIcon, LucideWrench, LucideHammer, LucideZap, LucideSparkles, LucidePaintBucket, LucideWashingMachine, LucideArrowRight } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-categories',
  imports: [RouterLink, LucideDynamicIcon, TranslatePipe],
  templateUrl: './categories.html',
  styleUrl: './categories.css',
})
export class Categories {
  readonly arrow = LucideArrowRight;
  readonly categories = [
    { code: 'PLUMBING', label: 'categories.plumbing', icon: LucideWrench },
    { code: 'CLEANING', label: 'categories.cleaning', icon: LucideSparkles },
    { code: 'APPLIANCE_REPAIR', label: 'landing.applianceRepair', icon: LucideWashingMachine },
    { code: 'ELECTRICAL', label: 'categories.electrical', icon: LucideZap },
    { code: 'PAINTING', label: 'categories.painting', icon: LucidePaintBucket },
    { code: 'CARPENTRY', label: 'categories.carpentry', icon: LucideHammer },
  ] as const;
}
