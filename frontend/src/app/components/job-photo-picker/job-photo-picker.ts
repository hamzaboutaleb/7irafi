import { Component, input, output } from '@angular/core';
import { LucidePlus, LucideX } from '@lucide/angular';

import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { JobPhoto } from '../../types/job.js';

@Component({
  selector: 'app-job-photo-picker',
  imports: [TranslatePipe, LucidePlus, LucideX],
  templateUrl: './job-photo-picker.html',
  styleUrl: './job-photo-picker.css',
})
export class JobPhotoPicker {
  readonly photos = input.required<readonly JobPhoto[]>();
  readonly errors = input.required<readonly string[]>();
  readonly maxPhotos = input.required<number>();
  readonly disabled = input(false);
  readonly locked = input(false);
  readonly filesSelected = output<File[]>();
  readonly photoRemoved = output<JobPhoto>();

  selectFiles(event: Event): void {
    if (this.disabled() || this.locked()) return;
    const input = event.target as HTMLInputElement;
    this.filesSelected.emit(Array.from(input.files ?? []));
    input.value = '';
  }
}
