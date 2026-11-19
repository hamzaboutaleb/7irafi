import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LucideUserRound } from '@lucide/angular';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { UserService } from '../../services/user.js';
import { PublicUserProfile } from '../../types/user.js';

@Component({
  selector: 'app-job-owner-card',
  imports: [TranslatePipe, LucideUserRound],
  templateUrl: './job-owner-card.html',
  styleUrl: './job-owner-card.css',
})
export class JobOwnerCard implements OnInit {
  private readonly users = inject(UserService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);

  readonly ownerId = input.required<string>();
  readonly createdAt = input.required<string>();

  readonly owner = signal<PublicUserProfile | null>(null);
  readonly ownerState = signal<'loading' | 'loaded' | 'error'>('loading');
  readonly ownerImageFailed = signal(false);

  readonly ownerName = computed(() => {
    const owner = this.owner();
    return owner
      ? `${owner.firstName} ${owner.lastName}`.trim()
      : this.i18n.translate('chat.jobOwner');
  });
  readonly dateLabel = computed(() =>
    new Intl.DateTimeFormat(this.i18n.language(), { dateStyle: 'medium' }).format(
      new Date(this.createdAt()),
    ),
  );

  ngOnInit(): void {
    this.loadOwner();
  }

  loadOwner(): void {
    const userId = this.ownerId();
    if (!userId) return;
    this.ownerState.set('loading');
    this.owner.set(null);
    this.ownerImageFailed.set(false);
    this.users
      .getPublicUserProfile(userId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.owner.set(response.data);
          this.ownerState.set('loaded');
        },
        error: () => this.ownerState.set('error'),
      });
  }
}
