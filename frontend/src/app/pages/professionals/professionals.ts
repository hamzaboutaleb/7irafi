import { Component, DestroyRef, computed, inject, OnInit, signal } from '@angular/core';
import { ApiError, Page } from '../../types/api.js';
import {
  ProfessionalCard,
  ServiceCategory,
  ServiceCity,
} from '../../types/professional-profile.js';
import { UserService } from '../../services/user.js';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-professionals',
  imports: [Navbar, Footer, RouterLink],
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
  readonly cities = signal<ServiceCity[]>([]);
  readonly categories = signal<ServiceCategory[]>([]);
  readonly filtersUnavailable = signal(false);
  readonly selectedCity = signal<string | null>(null);
  readonly selectedCategory = signal<string | null>(null);

  readonly canGoForward = computed(() => {
    const current = this.page();
    if (current === null) {
      return false;
    }
    return current.page < current.totalPages - 1;
  });

  readonly canGoBack = computed(() => {
    const current = this.page();
    if (current === null) {
      return false;
    }
    return current.page > 0;
  });

  ngOnInit(): void {
    this.userService
      .getServiceCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.categories.set(response.data);
        },
        error: () => {
          this.filtersUnavailable.set(true);
        },
      });
    this.userService
      .getServiceCities()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.cities.set(response.data);
        },
        error: () => {
          this.filtersUnavailable.set(true);
        },
      });
    this.load();
  }

  private load(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.userService
      .getProfessionals(
        this.pageNumber(),
        this.size(),
        this.selectedCity(),
        this.selectedCategory(),
      )
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

  nextPage(): void {
    if (!this.canGoForward()) {
      return;
    }
    this.pageNumber.update((value) => value + 1);
    this.load();
  }

  prevPage(): void {
    if (!this.canGoBack()) {
      return;
    }
    this.pageNumber.update((value) => value - 1);
    this.load();
  }

  private reloadFromFirstPage(): void {
    this.pageNumber.set(0);
    this.load();
  }

  cityFilter(city: string | null): void {
    this.selectedCity.set(city);
    this.reloadFromFirstPage();
  }

  categoryFilter(category: string | null): void {
    this.selectedCategory.set(category);
    this.reloadFromFirstPage();
  }

  clearAll(): void {
    this.selectedCity.set(null);
    this.selectedCategory.set(null);
    this.reloadFromFirstPage();
  }
}
