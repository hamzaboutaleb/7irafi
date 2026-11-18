import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { LucideCircleCheck } from '@lucide/angular';
import { TranslationKey } from '../../i18n/language.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { ApplicationService } from '../../services/application.js';
import { I18nService } from '../../services/i18n.js';
import { UserService } from '../../services/user.js';
import { JobResponse } from '../../types/job.js';

@Component({
  selector: 'app-job-application-action',
  imports: [RouterLink, TranslatePipe, LucideCircleCheck],
  templateUrl: './job-application-action.html',
  styleUrl: './job-application-action.css',
  host: { class: 'job-application-action' },
})
export class JobApplicationAction implements OnInit {
  private readonly applications = inject(ApplicationService);
  private readonly users = inject(UserService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly professionalEligibility = signal<
    'loading' | 'eligible' | 'ineligible' | 'error'
  >('loading');

  readonly job = input.required<JobResponse>();
  readonly currentUserId = input<string | null>(null);
  readonly identityFailed = input(false);
  readonly alreadyApplied = input(false);
  readonly showAction = input(true);

  readonly refreshRequested = output<void>();
  readonly identityRetryRequested = output<void>();

  readonly busy = signal(false);
  readonly applied = signal(false);
  readonly applyError = signal<string | null>(null);
  readonly isOwner = computed(() => this.currentUserId() === this.job().clientId);
  readonly eligibility = computed(() => {
    const professional = this.professionalEligibility();
    if (this.identityFailed() || professional === 'error') return 'error';
    if (!this.currentUserId()) return 'loading';
    return professional;
  });
  readonly canApply = computed(
    () =>
      this.eligibility() === 'eligible' &&
      !this.isOwner() &&
      this.job().status === 'OPEN' &&
      !this.busy() &&
      !this.applied(),
  );

  ngOnInit(): void {
    this.applied.set(this.alreadyApplied());
    this.loadEligibility();
  }

  loadEligibility(): void {
    this.professionalEligibility.set('loading');
    this.users
      .getCurrentProfessionalProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (professional) => {
          this.professionalEligibility.set(
            professional.data.onboardingStatus === 'ACTIVE' ? 'eligible' : 'ineligible',
          );
        },
        error: (error: HttpErrorResponse) => {
          this.professionalEligibility.set(error.status === 404 ? 'ineligible' : 'error');
        },
      });
  }

  retryEligibility(): void {
    if (this.identityFailed()) this.identityRetryRequested.emit();
    this.loadEligibility();
  }

  apply(): void {
    if (!this.canApply()) return;
    this.busy.set(true);
    this.applyError.set(null);
    this.applications
      .apply(this.job().id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.applied.set(true);
          this.busy.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.busy.set(false);
          if (error.error?.code === 'DUPLICATE_APPLICATION') {
            this.applied.set(true);
            return;
          }
          if (error.error?.code === 'WORKER_REQUIRED')
            this.professionalEligibility.set('ineligible');
          const messages: Record<string, TranslationKey> = {
            WORKER_REQUIRED: 'jobDetails.needsProfile',
            OWN_JOB_APPLICATION: 'jobDetails.ownJob',
            JOB_NOT_OPEN: 'jobDetails.closed',
            JOB_NOT_FOUND: 'jobDetails.noLongerAvailable',
            JOB_ACCESS_DENIED: 'jobDetails.accessDenied',
            APPLICATION_NOT_FOUND: 'jobDetails.applicationMissing',
          };
          this.applyError.set(
            this.i18n.translate(
              error.status === 0 || error.status === 503
                ? 'jobDetails.serviceUnavailable'
                : (messages[error.error?.code] ?? 'jobDetails.applyFailed'),
            ),
          );
          if (error.status === 409 || error.status === 404) this.refreshRequested.emit();
        },
      });
  }
}
