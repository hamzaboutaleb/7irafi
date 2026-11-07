import { DOCUMENT, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, ElementRef, HostListener, inject, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { RatingService } from '../../services/rating.js';
import { JobService } from '../../services/job.js';
import { JobResponse, JobStatus, UpdateJobRequest } from '../../types/job.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

import { I18nService } from '../../services/i18n.js';
import { TranslationKey } from '../../i18n/language.js';
import { LucideDynamicIcon, LucidePlus, LucideBriefcaseBusiness, LucideMapPin, LucideUsersRound, LucideCheck, LucideStar, LucideEllipsisVertical, LucidePencil, LucideTrash2, LucideX } from '@lucide/angular';

type Action = 'edit' | 'cancel' | 'complete' | 'delete';

@Component({
  selector: 'app-my-jobs',
  imports: [LucideDynamicIcon, TitleCasePipe, FormsModule, RouterLink, Navbar, Footer, JobDetailsModal, TranslatePipe],
  templateUrl: './my-jobs.html',
  styleUrl: './my-jobs.css',
})
export class MyJobs implements OnInit {
  private readonly i18n = inject(I18nService);
  readonly icons = { plus: LucidePlus, work: LucideBriefcaseBusiness, city: LucideMapPin, people: LucideUsersRound, check: LucideCheck, review: LucideStar, more: LucideEllipsisVertical, edit: LucidePencil, delete: LucideTrash2, cancel: LucideX };
  statusLabel(status: JobStatus): TranslationKey { return `dashboard.status.${status}`; }
  formatNumber(value: number): string { return new Intl.NumberFormat(this.i18n.language()).format(value); }
  formatDate(value: string): string { return new Intl.DateTimeFormat(this.i18n.language(), { dateStyle: 'medium' }).format(new Date(value)); }
  private readonly service = inject(JobService);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private previousOverflow: string | null = null;
  readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('actionDialog');
  readonly jobs = signal<JobResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly ratings = inject(RatingService);
  readonly completedJobToReview = signal<JobResponse | null>(null);
  readonly startWithReview = signal(false);
  readonly notice = signal<string | null>(null);
  readonly status = signal<JobStatus | ''>('');
  readonly statuses: JobStatus[] = ['OPEN', 'ASSIGNED', 'COMPLETED', 'CANCELLED'];
  readonly visibleJobs = computed(() => this.jobs().filter(job => !this.status() || job.status === this.status()));
  readonly selectedJobId = signal<string | null>(null);
  readonly target = signal<JobResponse | null>(null);
  readonly action = signal<Action>('edit');
  readonly busy = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly actionLabels: Record<Action, TranslationKey> = {
    edit: 'myJobs.edit', cancel: 'myJobs.cancel', complete: 'myJobs.complete', delete: 'myJobs.delete',
  };
  draft: UpdateJobRequest = { title: '', description: '', addressLine: '' };

  constructor() {
    this.destroyRef.onDestroy(() => this.restoreScroll());
  }

  ngOnInit(): void { this.load(); }

  openReview(jobId: string): void {
    this.startWithReview.set(true);
    this.selectedJobId.set(jobId);
  }

  closeMenu(menu: HTMLDetailsElement): void {
    menu.open = false;
    menu.querySelector('summary')?.focus();
  }

  closeMenuOnFocusOut(event: FocusEvent, menu: HTMLDetailsElement): void {
    if (!menu.contains(event.relatedTarget as Node | null)) menu.open = false;
  }

  @HostListener('document:click', ['$event'])
  closeMenusOutside(event: MouseEvent): void {
    this.element.nativeElement.querySelectorAll<HTMLDetailsElement>('.job-actions[open]')
      .forEach(menu => {
        if (!menu.contains(event.target as Node)) menu.open = false;
      });
  }

  load(): void {
    if (this.loading() || this.busy()) return;
    this.loading.set(true);
    this.error.set(null);
    this.service.getMyJobs().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: jobs => {
        this.jobs.set([...jobs].sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(error.status === 0 ? this.i18n.translate('myJobs.connectionError')
          : this.errorMessage(error, this.i18n.translate('myJobs.loadError')));
        this.loading.set(false);
      },
    });
  }

  allowed(job: JobResponse, action: Action): boolean {
    switch (action) {
      case 'edit': return job.status === 'OPEN';
      case 'cancel': return job.status === 'OPEN';
      case 'complete': return job.status === 'ASSIGNED';
      case 'delete': return job.status !== 'ASSIGNED';
    }
  }

  openAction(job: JobResponse, action: Action): void {
    if (this.busy() || !this.allowed(job, action)) return;
    this.target.set(job);
    this.action.set(action);
    this.actionError.set(null);
    this.draft = { title: job.title, description: job.description, addressLine: job.addressLine ?? '' };
    this.previousOverflow = this.document.body.style.overflow;
    this.document.body.style.overflow = 'hidden';
    this.dialog().nativeElement.showModal();
  }

  closeAction(event?: Event): void {
    event?.preventDefault();
    if (this.busy()) return;
    this.dialog().nativeElement.close();
    this.target.set(null);
    this.restoreScroll();
  }

  private restoreScroll(): void {
    if (this.previousOverflow !== null) {
      this.document.body.style.overflow = this.previousOverflow;
      this.previousOverflow = null;
    }
  }

  submit(): void {
    const job = this.target();
    const action = this.action();
    if (!job || this.busy() || !this.allowed(job, action)) return;
    const request = {
      title: this.draft.title.trim(), description: this.draft.description.trim(),
      addressLine: this.draft.addressLine?.trim() || null,
    };
    if (action === 'edit' && (!request.title || request.title.length > 120 ||
      !request.description || (request.addressLine?.length ?? 0) > 200)) {
      this.actionError.set(this.i18n.translate('myJobs.validation'));
      return;
    }
    this.busy.set(true);
    this.actionError.set(null);
    this.notice.set(null);
    let operation: Observable<JobResponse | void>;
    switch (action) {
      case 'edit': operation = this.service.updateJob(job.id, request); break;
      case 'cancel': operation = this.service.cancelJob(job.id); break;
      case 'complete': operation = this.service.completeJob(job.id); break;
      case 'delete': operation = this.service.deleteJob(job.id); break;
    }
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: updated => {
        this.jobs.update(jobs => action === 'delete' ? jobs.filter(item => item.id !== job.id)
          : jobs.map(item => item.id === job.id && updated ? updated : item));
        this.busy.set(false);
        this.closeAction();
        if (action === 'complete' && updated) this.completedJobToReview.set(updated);
        this.notice.set({ edit: this.i18n.translate('myJobs.updated'), cancel: this.i18n.translate('myJobs.cancelled'), complete: this.i18n.translate('myJobs.completed'), delete: this.i18n.translate('myJobs.deleted') }[action]);
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        if ([403, 404, 409].includes(error.status)) {
          this.closeAction();
          this.notice.set(this.errorMessage(error, this.i18n.translate('myJobs.changed')));
          this.load();
        } else {
          this.actionError.set(this.errorMessage(error, this.i18n.translate('myJobs.saveError')));
        }
      },
    });
  }

  private errorMessage(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 0) return this.i18n.translate('myJobs.uncertain');
    return error.error?.error?.message ?? error.error?.message ?? fallback;
  }
}
