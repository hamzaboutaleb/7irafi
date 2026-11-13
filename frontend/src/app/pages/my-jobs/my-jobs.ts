import { TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  computed,
  DestroyRef,
  ElementRef,
  HostListener,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import {
  LucideDynamicIcon,
  LucideBriefcaseBusiness,
  LucideMapPin,
  LucideUsersRound,
  LucideCheck,
  LucideStar,
  LucideEllipsisVertical,
  LucidePencil,
  LucideTrash2,
  LucideX,
} from '@lucide/angular';
import { Footer } from '../../components/footer/footer.js';
import { JobActionDialog } from '../../components/job-action-dialog/job-action-dialog.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { JobService } from '../../services/job.js';
import { RatingService } from '../../services/rating.js';
import { JobAction, JobActionResult, JobResponse, JobStatus } from '../../types/job.js';
import { canPerformJobAction } from '../../utils/job-actions.js';

@Component({
  selector: 'app-my-jobs',
  imports: [
    LucideDynamicIcon,
    TitleCasePipe,
    RouterLink,
    Navbar,
    Footer,
    JobActionDialog,
    JobDetailsModal,
    TranslatePipe,
  ],
  templateUrl: './my-jobs.html',
})
export class MyJobs implements OnInit {
  private readonly i18n = inject(I18nService);
  private readonly service = inject(JobService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly ratings = inject(RatingService);

  readonly icons = {
    work: LucideBriefcaseBusiness,
    city: LucideMapPin,
    people: LucideUsersRound,
    check: LucideCheck,
    review: LucideStar,
    more: LucideEllipsisVertical,
    edit: LucidePencil,
    delete: LucideTrash2,
    cancel: LucideX,
  };

  readonly jobs = signal<JobResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly completedJobToReview = signal<JobResponse | null>(null);
  readonly startWithReview = signal(false);
  readonly notice = signal<string | null>(null);
  readonly noticeTone = signal<'success' | 'danger'>('success');
  readonly status = signal<JobStatus | ''>('');
  readonly statuses: JobStatus[] = ['OPEN', 'ASSIGNED', 'COMPLETED', 'CANCELLED'];

  readonly visibleJobs = computed(() =>
    this.jobs().filter((job) => !this.status() || job.status === this.status()),
  );

  readonly selectedJobId = signal<string | null>(null);
  readonly pendingAction = signal<{ job: JobResponse; action: JobAction } | null>(null);
  readonly allowed = canPerformJobAction;

  ngOnInit(): void {
    this.load();
  }

  statusLabel(status: JobStatus): TranslationKey {
    return `dashboard.status.${status}`;
  }

  formatNumber(value: number): string {
    return new Intl.NumberFormat(this.i18n.language()).format(value);
  }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat(this.i18n.language(), { dateStyle: 'medium' }).format(
      new Date(value),
    );
  }

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
    this.element.nativeElement
      .querySelectorAll<HTMLDetailsElement>('.job-actions[open]')
      .forEach((menu) => {
        if (!menu.contains(event.target as Node)) menu.open = false;
      });
  }

  load(): void {
    if (this.loading() || this.pendingAction()) return;
    this.loading.set(true);
    this.error.set(null);
    this.service
      .getMyJobs()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (jobs) => {
          this.jobs.set([...jobs].sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
          this.loading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.error.set(
            error.status === 0
              ? this.i18n.translate('myJobs.connectionError')
              : (error.error?.error?.message ??
                  error.error?.message ??
                  this.i18n.translate('myJobs.loadError')),
          );
          this.loading.set(false);
        },
      });
  }

  openAction(job: JobResponse, action: JobAction): void {
    if (!this.pendingAction() && this.allowed(job, action)) {
      this.pendingAction.set({ job, action });
    }
  }

  actionCompleted(result: JobActionResult): void {
    this.jobs.update((jobs) =>
      result.action === 'delete'
        ? jobs.filter((job) => job.id !== result.jobId)
        : jobs.map((job) => (job.id === result.job.id ? result.job : job)),
    );
    this.pendingAction.set(null);
    if (result.action === 'complete') this.completedJobToReview.set(result.job);

    const messages: Record<JobAction, TranslationKey> = {
      edit: 'myJobs.updated',
      cancel: 'myJobs.cancelled',
      complete: 'myJobs.completed',
      delete: 'myJobs.deleted',
    };
    this.noticeTone.set(
      result.action === 'cancel' || result.action === 'delete' ? 'danger' : 'success',
    );
    this.notice.set(this.i18n.translate(messages[result.action]));
  }

  refreshJobs(message: string): void {
    this.pendingAction.set(null);
    this.noticeTone.set('danger');
    this.notice.set(message);
    this.load();
  }
}
