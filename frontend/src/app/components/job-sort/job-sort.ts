import { Component, computed, inject, input, output } from '@angular/core';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { Select } from '../select/select.js';

@Component({
  selector: 'app-job-sort',
  imports: [Select, TranslatePipe],
  templateUrl: './job-sort.html',
  styleUrl: './job-sort.css',
})
export class JobSort {
  private readonly i18n = inject(I18nService);

  readonly sort = input.required<string>();

  readonly sortChange = output<string>();

  readonly sortOptions = computed(() => {
    return [
      { value: 'createdAt,desc', label: this.i18n.translate('jobs.newest') },
      { value: 'budget,desc', label: this.i18n.translate('jobs.budgetHigh') },
      { value: 'budget,asc', label: this.i18n.translate('jobs.budgetLow') },
    ];
  });
}
