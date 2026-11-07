import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { Page } from '../../types/api.js';
import { ProfessionalCard, PublicProfessionalProfile, ServiceCategory, ServiceCity } from '../../types/professional-profile.js';
import { PublicUserProfile } from '../../types/user.js';
import { UserService } from '../../services/user.js';
import { NetworkService } from '../../services/network.js';
import { I18nService } from '../../services/i18n.js';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, map, of, Subscription, switchMap } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { Select } from '../../components/select/select.js';
import { RatingStars } from '../../components/rating-stars/rating-stars.js';
import { RatingService, UserRatingSummary } from '../../services/rating.js';
import { LucideDynamicIcon, LucideMapPin, LucideWrench, LucideBookmark, LucideX, LucideUsersRound, LucideChevronLeft, LucideChevronRight } from '@lucide/angular';

@Component({
  selector: 'app-professionals',
  imports: [Navbar, Footer, RouterLink, TranslatePipe, Select, LucideDynamicIcon, RatingStars],
  templateUrl: './professionals.html',
})
export class Professionals implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly network = inject(NetworkService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly ratings = inject(RatingService);
  private request?: Subscription;
  private profileRequest?: Subscription;
  private ratingsRequest?: Subscription;
  private previousOverflow: string | null = null;
  readonly profileDialog = viewChild.required<ElementRef<HTMLDialogElement>>('profileDialog');
  readonly pageNumber = signal(0);
  readonly page = signal<Page<ProfessionalCard> | null>(null);
  readonly isLoading = signal(true);
  readonly failed = signal(false);
  readonly cities = signal<ServiceCity[]>([]);
  readonly categories = signal<ServiceCategory[]>([]);
  readonly citiesFailed = signal(false);
  readonly categoriesFailed = signal(false);
  readonly selectedCity = signal('');
  readonly selectedCategory = signal('');
  readonly savedIds = this.network.savedIds;
  readonly people = signal<Record<string, PublicUserProfile>>({});
  readonly failedImages = signal<ReadonlySet<string>>(new Set());
  readonly selected = signal<ProfessionalCard | null>(null);
  readonly profile = signal<PublicProfessionalProfile | null>(null);
  readonly profileLoading = signal(false);
  readonly profileFailed = signal(false);
  readonly saving = signal<ReadonlySet<string>>(new Set());
  readonly saveError = signal<string | null>(null);
  readonly ratingSummary = signal<UserRatingSummary | null>(null);
  readonly ratingsLoading = signal(false);
  readonly icons = { city: LucideMapPin, service: LucideWrench, save: LucideBookmark, close: LucideX, people: LucideUsersRound, previous: LucideChevronLeft, next: LucideChevronRight };
  readonly cityOptions = computed(() => [{ value: '', label: this.i18n.translate('directory.allCities') }, ...this.cities().map(city => ({ value: city.code, label: this.label(city.displayName) }))]);
  readonly categoryOptions = computed(() => [{ value: '', label: this.i18n.translate('directory.allServices') }, ...this.categories().map(category => ({ value: category.code, label: this.label(category.displayName) }))]);
  readonly hasFilters = computed(() => !!(this.selectedCity() || this.selectedCategory()));
  readonly canGoBack = computed(() => !this.isLoading() && (this.page()?.page ?? 0) > 0);
  readonly canGoForward = computed(() => !this.isLoading() && !!this.page() && this.page()!.page + 1 < this.page()!.totalPages);

  constructor() { this.destroyRef.onDestroy(() => this.restoreScroll()); }

  ngOnInit(): void {
    this.loadFilters();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.selectedCategory.set(params.get('category') || '');
      this.selectedCity.set(params.get('city') || '');
      this.pageNumber.set(0); this.load();
    });
  }

  label(value: string): string { return value.replaceAll('_', ' ').replace(/\S+/g, word => word.charAt(0).toLocaleUpperCase() + word.slice(1).toLocaleLowerCase()); }
  name(person: ProfessionalCard): string {
    const identity = this.people()[person.userId];
    return identity ? [identity.firstName, identity.lastName].filter(Boolean).join(' ') : person.firstName;
  }
  initials(person: ProfessionalCard): string { return this.name(person).split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join('').toLocaleUpperCase(); }
  avatar(person: ProfessionalCard): string | null { return this.people()[person.userId]?.avatarUrl ?? person.avatarUrl; }
  imageFailed(id: string): void { this.failedImages.update(ids => new Set([...ids, id])); }
  number(value: number): string { return new Intl.NumberFormat(this.i18n.language()).format(value); }
  score(value: number): string { return new Intl.NumberFormat(this.i18n.language(), { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value); }
  pageLabel(): string { return this.i18n.translate('directory.pageLabel', { page: this.number((this.page()?.page ?? 0) + 1), total: this.number(this.page()?.totalPages ?? 0) }); }

  loadFilters(): void {
    this.categoriesFailed.set(false); this.citiesFailed.set(false);
    this.userService.getServiceCategories().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: response => this.categories.set(response.data), error: () => this.categoriesFailed.set(true) });
    this.userService.getServiceCities().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: response => this.cities.set(response.data), error: () => this.citiesFailed.set(true) });
  }

  load(): void {
    this.request?.unsubscribe();
    this.isLoading.set(true); this.failed.set(false);
    this.request = this.userService.getProfessionals(this.pageNumber(), 12, this.selectedCity() || null, this.selectedCategory() || null).pipe(
      switchMap(response => {
        const lookups = response.data.content.filter(person => !this.people()[person.userId]).map(person => this.userService.getPublicUserProfile(person.userId).pipe(map(result => result.data), catchError(() => of(null))));
        return (lookups.length ? forkJoin(lookups) : of([])).pipe(map(people => ({ result: response.data, people })));
      }), takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ result, people }) => {
        this.people.update(known => { const next = { ...known }; for (const person of people) if (person) next[person.id] = person; return next; });
        this.page.set(result); this.isLoading.set(false);
      }, error: () => { this.failed.set(true); this.isLoading.set(false); },
    });
  }

  nextPage(): void { if (this.canGoForward()) { this.pageNumber.update(value => value + 1); this.load(); } }
  prevPage(): void { if (this.canGoBack()) { this.pageNumber.update(value => value - 1); this.load(); } }
  cityFilter(value: string): void { void this.router.navigate([], { relativeTo: this.route, queryParams: { city: value || null }, queryParamsHandling: 'merge', replaceUrl: true }); }
  categoryFilter(value: string): void { void this.router.navigate([], { relativeTo: this.route, queryParams: { category: value || null }, queryParamsHandling: 'merge', replaceUrl: true }); }
  clearAll(): void { void this.router.navigate([], { relativeTo: this.route, queryParams: { category: null, city: null }, queryParamsHandling: 'merge', replaceUrl: true }); }

  toggleSave(userId: string): void {
    if (this.saving().has(userId)) return;
    this.saving.update(ids => new Set([...ids, userId])); this.saveError.set(null);
    const operation = this.savedIds().has(userId) ? this.userService.deleteConnection(userId) : this.userService.addConnection(userId);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        // Reconcile through the shared network so the contacts rail stays in sync.
        this.network.load(); this.finishSave(userId);
      }, error: () => { this.saveError.set(userId); this.finishSave(userId); },
    });
  }
  private finishSave(id: string): void { this.saving.update(ids => { const next = new Set(ids); next.delete(id); return next; }); }

  openProfile(person: ProfessionalCard): void {
    this.selected.set(person); this.profile.set(null);
    this.previousOverflow = this.document.body.style.overflow;
    this.document.body.style.overflow = 'hidden';
    this.ratingSummary.set(null);
    this.profileDialog().nativeElement.showModal(); this.loadProfile(); this.loadRatings();
  }
  loadProfile(): void {
    const person = this.selected(); if (!person) return;
    this.profileRequest?.unsubscribe(); this.profileLoading.set(true); this.profileFailed.set(false);
    this.profileRequest = this.userService.getProfessional(person.userId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => { this.profile.set(response.data); this.profileLoading.set(false); },
      error: () => { this.profileFailed.set(true); this.profileLoading.set(false); },
    });
  }
  loadRatings(): void {
    const person = this.selected(); if (!person) return;
    this.ratingsRequest?.unsubscribe(); this.ratingsLoading.set(true);
    this.ratingsRequest = this.ratings.getSummary(person.userId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: summary => { this.ratingSummary.set(summary); this.ratingsLoading.set(false); },
      error: () => { this.ratingSummary.set(null); this.ratingsLoading.set(false); },
    });
  }
  dismissProfile(): void { this.profileRequest?.unsubscribe(); this.ratingsRequest?.unsubscribe(); this.selected.set(null); this.restoreScroll(); }
  private restoreScroll(): void { if (this.previousOverflow !== null) { this.document.body.style.overflow = this.previousOverflow; this.previousOverflow = null; } }
}
