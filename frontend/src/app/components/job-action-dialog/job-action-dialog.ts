import { HttpErrorResponse } from '@angular/common/http';
import {
  AfterViewInit,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  OnDestroy,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { LucideX } from '@lucide/angular';
import { Observable } from 'rxjs';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { JobService } from '../../services/job.js';
import { JobAction, JobActionResult, JobResponse, UpdateJobRequest } from '../../types/job.js';
import { canPerformJobAction } from '../../utils/job.js';

@Component({
  selector: 'app-job-action-dialog',
  imports: [FormsModule, TranslatePipe, LucideX],
  templateUrl: './job-action-dialog.html',
  styleUrl: './job-action-dialog.css',
})
export class JobActionDialog implements OnInit, AfterViewInit, OnDestroy {
  private readonly service = inject(JobService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private closeTimer?: ReturnType<typeof setTimeout>;

  readonly job = input.required<JobResponse>();
  readonly action = input.required<JobAction>();

  readonly completed = output<JobActionResult>();
  readonly refreshRequested = output<void>();
  readonly closed = output<void>();

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<JobActionResult | null>(null);
  readonly needsRefresh = signal(false);

  readonly actionLabels: Record<JobAction, TranslationKey> = {
    edit: 'myJobs.edit',
    cancel: 'myJobs.cancel',
    complete: 'myJobs.complete',
    delete: 'myJobs.delete',
  };

  readonly successLabels: Record<JobAction, TranslationKey> = {
    edit: 'myJobs.updated',
    cancel: 'myJobs.cancelled',
    complete: 'myJobs.completed',
    delete: 'myJobs.deleted',
  };

  draft: UpdateJobRequest = {
    title: '',
    description: '',
    addressLine: '',
  };

  ngOnInit(): void {
    const job = this.job();

    this.draft = {
      title: job.title,
      description: job.description,
      addressLine: job.addressLine ?? '',
    };
  }

  ngAfterViewInit(): void {
    this.dialog().nativeElement.showModal();
  }

  ngOnDestroy(): void {
    clearTimeout(this.closeTimer);
    this.dialog().nativeElement.close();
  }

  close(event?: Event): void {
    event?.preventDefault();
    if (!this.busy()) this.dialog().nativeElement.close();
  }

  onClosed(): void {
    clearTimeout(this.closeTimer);
    const result = this.result();
    if (result) {
      this.completed.emit(result);
    } else if (this.needsRefresh()) {
      this.refreshRequested.emit();
    } else {
      this.closed.emit();
    }
  }

  submit(): void {
    const job = this.job();
    const action = this.action();
    if (this.busy() || this.result() || this.needsRefresh() || !canPerformJobAction(job, action))
      return;

    let operation: Observable<JobResponse | void>;
    switch (action) {
      case 'edit': {
        const request: UpdateJobRequest = {
          title: this.draft.title.trim(),
          description: this.draft.description.trim(),
          addressLine: this.draft.addressLine?.trim() || null,
        };
        if (
          !request.title ||
          request.title.length > 120 ||
          !request.description ||
          (request.addressLine?.length ?? 0) > 200
        ) {
          this.error.set(this.i18n.translate('myJobs.validation'));
          return;
        }

        operation = this.service.updateJob(job.id, request);
        break;
      }
      case 'cancel':
        operation = this.service.cancelJob(job.id);
        break;
      case 'complete':
        operation = this.service.completeJob(job.id);
        break;
      case 'delete':
        operation = this.service.deleteJob(job.id);
        break;
    }

    this.busy.set(true);
    this.error.set(null);

    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.result.set(
          action === 'delete'
            ? {
                action,
                jobId: job.id,
              }
            : {
                action,
                job: updated ?? job,
              },
        );
        this.closeTimer = setTimeout(() => this.close(), 2000);
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        if ([403, 404, 409].includes(error.status)) {
          this.needsRefresh.set(true);
        } else {
          this.error.set(this.i18n.translate('myJobs.saveError'));
        }
      },
    });
  }
}
