import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { LucideDynamicIcon, LucideMapPin, LucideWrench, LucideArrowRight } from '@lucide/angular';

@Component({
  selector: 'app-hero',
  imports: [RouterLink, TranslatePipe, LucideDynamicIcon],
  templateUrl: './hero.html',
  styleUrl: './hero.css',
})
export class Hero {
  private readonly i18n = inject(I18nService);
  readonly icons = { city: LucideMapPin, service: LucideWrench, arrow: LucideArrowRight };
  budget(): string { return new Intl.NumberFormat(this.i18n.language()).format(650); }
}
