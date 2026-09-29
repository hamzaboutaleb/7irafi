import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UserService } from '../../services/user.js';
import { UserProfile } from '../../types/user.js';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../../types/api.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-dashboard',
  imports: [TranslatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  private readonly userService = inject(UserService);
  private readonly destroyRef = inject(DestroyRef);
  readonly errorMessage = signal<string | null>(null);

  readonly profile = signal<UserProfile | null>(null);
  readonly isLoading = signal(true);

  ngOnInit(): void {
    this.userService
      .getCurrentUser()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.profile.set(response.data);
          this.isLoading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;

          this.errorMessage.set(
            body?.error?.message ?? 'Unable to load your dashboard.',
          );
          this.isLoading.set(false);
        },
      });
  }
}
