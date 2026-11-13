import { Component, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  LucideCheck,
  LucideDynamicIcon,
  LucideEllipsisVertical,
  LucidePencil,
  LucideStar,
  LucideTrash2,
  LucideUsersRound,
  LucideX,
} from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { RatingService } from '../../services/rating.js';
import { JobAction, JobActionResult, JobResponse } from '../../types/job.js';
import { canPerformJobAction } from '../../utils/job.js';
import { JobActionDialog } from '../job-action-dialog/job-action-dialog.js';

@Component({
  selector: 'app-my-job-actions',
  imports: [RouterLink, TranslatePipe, LucideDynamicIcon, JobActionDialog],
  templateUrl: './my-job-actions.html',
  styleUrl: './my-job-actions.css',
})
export class MyJobActions {
  readonly ratings = inject(RatingService);

  readonly job = input.required<JobResponse>();

  readonly completed = output<JobActionResult>();
  readonly refreshRequested = output<void>();
  readonly detailsRequested = output<void>();
  readonly reviewRequested = output<void>();

  readonly selectedAction = signal<JobAction | null>(null);
  readonly menuOpen = signal(false);

  readonly allowed = canPerformJobAction;
  readonly icons = {
    people: LucideUsersRound,
    check: LucideCheck,
    review: LucideStar,
    more: LucideEllipsisVertical,
    edit: LucidePencil,
    delete: LucideTrash2,
    cancel: LucideX,
  };

  openAction(action: JobAction): void {
    if (!this.selectedAction()) {
      this.selectedAction.set(action);
    }
  }

  actionCompleted(result: JobActionResult): void {
    this.selectedAction.set(null);
    this.completed.emit(result);
  }

  refreshJobs(): void {
    this.selectedAction.set(null);
    this.refreshRequested.emit();
  }

  closeMenuOutside(menu: HTMLDetailsElement, target: EventTarget | null): void {
    if (menu.open && !menu.contains(target as Node | null)) menu.open = false;
  }
}
