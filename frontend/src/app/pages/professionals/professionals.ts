import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { ApiError, Page } from '../../types/api.js';
import { ProfessionalCard } from '../../types/professional-profile.js';
import { UserService } from '../../services/user.js';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-professionals',
  imports: [],
  templateUrl: './professionals.html',
  styleUrl: './professionals.css',
})
export class Professionals implements OnInit {
  private readonly userService = inject(UserService);
  private readonly destroyRef = inject(DestroyRef);

  readonly size = signal<number>(12);
  readonly pageNumber = signal<number>(0);
  readonly page = signal<Page<ProfessionalCard> | null>(null);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.userService
      .getProfessionals(this.pageNumber(), this.size())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.page.set(response.data);
          this.isLoading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          const body = err.error as ApiError | undefined;

          this.errorMessage.set(body?.error?.message ?? 'Unable to load this page.');
          this.isLoading.set(false);
        },
      });
  }
}
