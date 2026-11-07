import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { RouterLink } from '@angular/router';
import { LucideArrowRight, LucideCircleCheck, LucideStar, LucideUsersRound } from '@lucide/angular';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { JobDetailsModal } from '../../components/job-details-modal/job-details-modal.js';
import { DashboardSidebar } from '../../components/dashboard-sidebar/dashboard-sidebar.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { TranslationKey } from '../../i18n/language.js';
import { UserService } from '../../services/user.js';
import { AuthService } from '../../services/auth.js';
import { DashboardData, DashboardJob, DashboardSnapshot, DashboardView } from '../../services/dashboard.js';
import { RatingService, UserRatingSummary } from '../../services/rating.js';
import { UserProfile } from '../../types/user.js';
import { ProfessionalProfile } from '../../types/professional-profile.js';

type LoadState = 'idle' | 'loading' | 'loaded' | 'error';

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, DecimalPipe, RouterLink, LucideArrowRight, LucideCircleCheck, LucideStar, LucideUsersRound, Navbar, Footer, JobDetailsModal, DashboardSidebar, TranslatePipe],
  providers: [DashboardData],
  templateUrl: './dashboard.html',
  host: { class: 'block' },
})
export class Dashboard implements OnInit {
  private readonly users = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly data = inject(DashboardData);
  private readonly requests: Partial<Record<DashboardView, Subscription>> = {};
  private readonly destroyRef = inject(DestroyRef);
  readonly ratings = inject(RatingService);

  readonly profile = signal<UserProfile | null>(null);
  readonly profileState = signal<LoadState>('idle');
  readonly professional = signal<ProfessionalProfile | null>(null);
  readonly professionalState = signal<LoadState>('idle');
  readonly summary = signal<UserRatingSummary | null>(null);
  readonly summaryState = signal<LoadState>('idle');
  readonly view = signal<DashboardView>(this.initialView());
  readonly hiring = signal<DashboardSnapshot | null>(null);
  readonly working = signal<DashboardSnapshot | null>(null);
  readonly hiringState = signal<LoadState>('idle');
  readonly workingState = signal<LoadState>('idle');
  readonly snapshot = computed(() => this.view() === 'hiring' ? this.hiring() : this.working());
  readonly workState = computed(() => this.view() === 'hiring' ? this.hiringState() : this.workingState());
  readonly selectedJob = signal<string | null>(null);
  readonly reviewOpen = signal(false);
  readonly activeProfessional = computed(() => this.professional()?.onboardingStatus === 'ACTIVE');
  readonly primaryRoute = computed(() => this.view() === 'hiring' ? '/jobs/new' : this.activeProfessional() ? '/jobs' : '/professional-profile');
  readonly primaryLabel = computed<TranslationKey>(() => this.view() === 'hiring'
    ? 'myJobs.post' : this.activeProfessional() ? 'dashboard.findWork' : 'dashboard.professionalProfile');

  ngOnInit(): void {
    this.loadProfile();
    this.loadProfessional();
    this.ratings.reviewsChanged.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      // Refresh after a review is published in the dialog; remove its reminder too.
      this.loadView(true);
      this.loadSummary();
    });
  }

  loadProfile(): void {
    if (this.profileState() === 'loading') return;
    this.profileState.set('loading');
    this.users.getCurrentUser().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        this.profile.set(response.data);
        this.profileState.set('loaded');
        this.loadView();
        this.loadSummary();
      },
      error: () => this.profileState.set('error'),
    });
  }

  selectView(view: DashboardView): void {
    this.view.set(view);
    try {
      localStorage.setItem(this.storageKey(), view);
    } catch {
      // Storage can be blocked. The switch still works for this visit.
    }
    this.loadView();
  }

  loadView(refresh = false): void {
    const userId = this.profile()?.id;
    const view = this.view();
    const state = view === 'hiring' ? this.hiringState : this.workingState;
    const result = view === 'hiring' ? this.hiring : this.working;
    if (!userId || (!refresh && (state() === 'loading' || state() === 'loaded'))) return;
    // Cancel an older refresh so a late response cannot restore an outdated reminder.
    this.requests[view]?.unsubscribe();
    state.set('loading');
    const request = view === 'hiring' ? this.data.loadHiring(userId) : this.data.loadWorking(userId);
    this.requests[view] = request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: snapshot => {
        result.set(snapshot);
        state.set('loaded');
      },
      error: () => state.set('error'),
    });
  }

  loadProfessional(): void {
    if (this.professionalState() === 'loading') return;
    this.professionalState.set('loading');
    this.users.getCurrentProfessionalProfile().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        this.professional.set(response.data);
        this.professionalState.set('loaded');
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 404) {
          this.professional.set(null);
          this.professionalState.set('loaded');
        } else {
          this.professionalState.set('error');
        }
      },
    });
  }

  loadSummary(): void {
    const userId = this.profile()?.id;
    if (!userId || this.summaryState() === 'loading') return;
    this.summaryState.set('loading');
    this.ratings.getSummary(userId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: summary => {
        this.summary.set(summary);
        this.summaryState.set('loaded');
      },
      error: () => this.summaryState.set('error'),
    });
  }

  openJob(jobId: string, review = false): void {
    this.reviewOpen.set(review);
    this.selectedJob.set(jobId);
  }

  canReview(row: DashboardJob): boolean {
    const job = row.job;
    const userId = this.profile()?.id;
    return !!job && job.status === 'COMPLETED' && !!job.assignedWorkerId &&
      (job.clientId === userId || job.assignedWorkerId === userId);
  }

  statusLabel(row: DashboardJob): TranslationKey {
    if (row.applicationStatus && row.applicationStatus !== 'ACCEPTED') {
      if (row.applicationStatus === 'PENDING' && row.job && row.job.status !== 'OPEN') {
        return 'dashboard.status.CLOSED';
      }
      return `dashboard.status.${row.applicationStatus}`;
    }
    if (row.job && row.job.status !== 'OPEN') return `dashboard.status.${row.job.status}`;
    if (row.applicationStatus) return `dashboard.status.${row.applicationStatus}`;
    return 'dashboard.status.OPEN';
  }

  emptyTitle(): TranslationKey {
    if (this.view() === 'hiring') return 'dashboard.hiringEmptyTitle';
    if (this.professionalState() !== 'loaded') return 'dashboard.workingEmptyTitle';
    if (this.professional()?.onboardingStatus === 'SUSPENDED') return 'dashboard.suspendedTitle';
    return this.activeProfessional() ? 'dashboard.workingEmptyTitle' : 'dashboard.setupTitle';
  }

  emptyHint(): TranslationKey {
    if (this.view() === 'hiring') return 'dashboard.hiringEmptyHint';
    if (this.professionalState() !== 'loaded') return 'dashboard.workingEmptyHint';
    if (this.professional()?.onboardingStatus === 'SUSPENDED') return 'dashboard.suspendedHint';
    return this.activeProfessional() ? 'dashboard.workingEmptyHint' : 'dashboard.setupHint';
  }

  private storageKey(): string {
    return `dashboard-view:${this.auth.currentUserId() ?? 'signed-in'}`;
  }

  private initialView(): DashboardView {
    try {
      return localStorage.getItem(this.storageKey()) === 'working' ? 'working' : 'hiring';
    } catch {
      return 'hiring';
    }
  }
}
