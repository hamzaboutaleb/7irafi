import { DOCUMENT } from '@angular/common';
import {
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import {
  LucideBookmark,
  LucideChevronLeft,
  LucideChevronRight,
  LucideDynamicIcon,
  LucideMapPin,
  LucideUsersRound,
  LucideWrench,
  LucideX,
} from '@lucide/angular';
import {
  catchError,
  forkJoin,
  map,
  Observable,
  of,
  Subject,
  Subscription,
  switchMap,
  tap,
} from 'rxjs';
import { Footer } from '../../components/footer/footer.js';
import { Navbar } from '../../components/navbar/navbar.js';
import { RatingStars } from '../../components/rating-stars/rating-stars.js';
import { Select } from '../../components/select/select.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { I18nService } from '../../services/i18n.js';
import { NetworkService } from '../../services/network.js';
import { RatingService, UserRatingSummary } from '../../services/rating.js';
import { UserService } from '../../services/user.js';
import { Page } from '../../types/api.js';
import {
  ProfessionalCard,
  PublicProfessionalProfile,
  ServiceCategory,
  ServiceCity,
} from '../../types/professional-profile.js';
import { PublicUserProfile } from '../../types/user.js';

// How many professionals one page shows.
const PAGE_SIZE = 12;

@Component({
  selector: 'app-professionals',
  imports: [Navbar, Footer, RouterLink, TranslatePipe, Select, LucideDynamicIcon, RatingStars],
  templateUrl: './professionals.html',
  styleUrl: './professionals.css',
})
export class Professionals implements OnInit {
  // services
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly network = inject(NetworkService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly ratings = inject(RatingService);

  // OLD:
  // // requests still running, so we can cancel them
  // private request?: Subscription;

  // NEW: every "show the list again" (filters, Next, Previous, Retry) goes through here
  private readonly reload$ = new Subject<void>();

  // popup requests still running, so closing the popup can cancel them
  private profileRequest?: Subscription;
  private ratingsRequest?: Subscription;

  // page scroll setting saved while the popup is open
  private previousOverflow: string | null = null;

  readonly icons = {
    city: LucideMapPin,
    service: LucideWrench,
    save: LucideBookmark,
    close: LucideX,
    people: LucideUsersRound,
    previous: LucideChevronLeft,
    next: LucideChevronRight,
  };

  // list state
  readonly pageNumber = signal(0);
  readonly page = signal<Page<ProfessionalCard> | null>(null);
  readonly isLoading = signal(true);
  readonly failed = signal(false);
  // full names and photos, by user id (the list only has first names)
  readonly people = signal<Record<string, PublicUserProfile>>({});
  // user ids whose photo did not load, so we show initials instead
  readonly failedImages = signal<ReadonlySet<string>>(new Set());

  // filters
  readonly cities = signal<ServiceCity[]>([]);
  readonly categories = signal<ServiceCategory[]>([]);
  readonly citiesFailed = signal(false);
  readonly categoriesFailed = signal(false);
  readonly selectedCity = signal('');
  readonly selectedCategory = signal('');

  // saved contacts
  readonly savedIds = this.network.savedIds;
  readonly saving = signal<ReadonlySet<string>>(new Set());
  readonly saveError = signal<string | null>(null);

  // profile popup
  readonly profileDialog = viewChild.required<ElementRef<HTMLDialogElement>>('profileDialog');
  readonly selected = signal<ProfessionalCard | null>(null);
  readonly profile = signal<PublicProfessionalProfile | null>(null);
  readonly profileLoading = signal(false);
  readonly profileFailed = signal(false);
  readonly ratingSummary = signal<UserRatingSummary | null>(null);
  readonly ratingsLoading = signal(false);

  // the choices of the two dropdowns, with "All ..." first
  readonly cityOptions = computed(() => [
    { value: '', label: this.i18n.translate('directory.allCities') },
    ...this.cities().map((city) => ({ value: city.code, label: this.label(city.displayName) })),
  ]);
  readonly categoryOptions = computed(() => [
    { value: '', label: this.i18n.translate('directory.allServices') },
    ...this.categories().map((category) => ({
      value: category.code,
      label: this.label(category.displayName),
    })),
  ]);

  readonly hasFilters = computed(
    () => this.selectedCity() !== '' || this.selectedCategory() !== '',
  );

  // Previous / Next buttons
  readonly canGoBack = computed(() => !this.isLoading() && (this.page()?.page ?? 0) > 0);
  readonly canGoForward = computed(() => {
    const page = this.page();
    return !this.isLoading() && page !== null && page.page + 1 < page.totalPages;
  });

  constructor() {
    // put the page scroll back if we leave while the popup is open
    this.destroyRef.onDestroy(() => this.restoreScroll());
  }

  // runs when the page opens
  ngOnInit(): void {
    this.loadFilters();

    // NEW: the list. Each reload$.next() fetches a page; switchMap drops an older one still loading.
    // Must be set up before queryParamMap below, which emits right away.
    this.reload$
      .pipe(
        tap(() => {
          this.isLoading.set(true);
          this.failed.set(false);
        }),
        switchMap(() => this.fetchPage()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((outcome) => {
        if (outcome === null) {
          this.failed.set(true);
          this.isLoading.set(false);
          return;
        }
        const { result, found } = outcome;
        this.people.update((known) => {
          const next = { ...known };
          for (const person of found) {
            if (person) next[person.id] = person;
          }
          return next;
        });
        this.page.set(result);
        this.isLoading.set(false);
      });

    // the filters live in the URL (?city=...&category=...): reload the list when they change
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.selectedCategory.set(params.get('category') ?? '');
      this.selectedCity.set(params.get('city') ?? '');
      this.pageNumber.set(0);
      this.load();
    });
  }

  // "HOME_CLEANING" -> "Home Cleaning"
  label(value: string): string {
    return value
      .replaceAll('_', ' ')
      .replace(
        /\S+/g,
        (word) => word.charAt(0).toLocaleUpperCase() + word.slice(1).toLocaleLowerCase(),
      );
  }

  // "Plumbing · Electrical", or null when the list is empty
  joinLabels(items: { displayName: string }[]): string | null {
    if (items.length === 0) return null;
    return items.map((item) => this.label(item.displayName)).join(' · ');
  }

  // full name if we have it, else the first name from the list
  name(person: ProfessionalCard): string {
    const identity = this.people()[person.userId];
    if (!identity) return person.firstName;
    return [identity.firstName, identity.lastName].filter(Boolean).join(' ');
  }

  // "Youssef Rachidi" -> "YR"
  initials(person: ProfessionalCard): string {
    const parts = this.name(person).split(/\s+/).filter(Boolean).slice(0, 2);
    return parts
      .map((part) => part[0])
      .join('')
      .toLocaleUpperCase();
  }

  // photo link, or null to show initials
  avatar(person: ProfessionalCard): string | null {
    if (this.failedImages().has(person.userId)) return null;
    return this.people()[person.userId]?.avatarUrl ?? person.avatarUrl;
  }

  // a photo could not load
  imageFailed(id: string): void {
    this.failedImages.update((ids) => new Set([...ids, id]));
  }

  // numbers written the way the current language writes them
  number(value: number): string {
    return new Intl.NumberFormat(this.i18n.language()).format(value);
  }

  // 4.5 -> "4.5" (always one decimal)
  score(value: number): string {
    return new Intl.NumberFormat(this.i18n.language(), {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    }).format(value);
  }

  // "Page 2 of 5"
  pageLabel(): string {
    return this.i18n.translate('directory.pageLabel', {
      page: this.number((this.page()?.page ?? 0) + 1),
      total: this.number(this.page()?.totalPages ?? 0),
    });
  }

  // load the city and service lists for the dropdowns (also the Retry button)
  loadFilters(): void {
    this.categoriesFailed.set(false);
    this.citiesFailed.set(false);
    this.userService
      .getServiceCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.categories.set(response.data),
        error: () => this.categoriesFailed.set(true),
      });
    this.userService
      .getServiceCities()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.cities.set(response.data),
        error: () => this.citiesFailed.set(true),
      });
  }

  // OLD (manual unsubscribe):
  // // load one page of professionals, then the full names we don't know yet
  // load(): void {
  //   this.request?.unsubscribe(); // forget the old page if it's still loading
  //   this.isLoading.set(true);
  //   this.failed.set(false);
  //
  //   this.request = this.userService
  //     .getProfessionals(
  //       this.pageNumber(),
  //       PAGE_SIZE,
  //       this.selectedCity() || null,
  //       this.selectedCategory() || null,
  //     )
  //     .pipe(
  //       switchMap((response) => {
  //         // one request per unknown person; a failed one just gives null
  //         const lookups = response.data.content
  //           .filter((person) => !this.people()[person.userId])
  //           .map((person) =>
  //             this.userService.getPublicUserProfile(person.userId).pipe(
  //               map((result) => result.data),
  //               catchError(() => of(null)),
  //             ),
  //           );
  //         const people = lookups.length > 0 ? forkJoin(lookups) : of([]);
  //         return people.pipe(map((found) => ({ result: response.data, found })));
  //       }),
  //       takeUntilDestroyed(this.destroyRef),
  //     )
  //     .subscribe({
  //       next: ({ result, found }) => {
  //         this.people.update((known) => {
  //           const next = { ...known };
  //           for (const person of found) {
  //             if (person) next[person.id] = person;
  //           }
  //           return next;
  //         });
  //         this.page.set(result);
  //         this.isLoading.set(false);
  //       },
  //       error: () => {
  //         this.failed.set(true);
  //         this.isLoading.set(false);
  //       },
  //     });
  // }

  // NEW: show the list again (filters, Next, Previous, Retry); the stream in ngOnInit does the work
  load(): void {
    this.reload$.next();
  }

  // NEW: one page of professionals + the full names we don't know yet; null if it failed
  private fetchPage(): Observable<{
    result: Page<ProfessionalCard>;
    found: (PublicUserProfile | null)[];
  } | null> {
    return this.userService
      .getProfessionals(
        this.pageNumber(),
        PAGE_SIZE,
        this.selectedCity() || null,
        this.selectedCategory() || null,
      )
      .pipe(
        switchMap((response) => {
          // one request per unknown person; a failed one just gives null
          const lookups = response.data.content
            .filter((person) => !this.people()[person.userId])
            .map((person) =>
              this.userService.getPublicUserProfile(person.userId).pipe(
                map((result) => result.data),
                catchError(() => of(null)),
              ),
            );
          const people = lookups.length > 0 ? forkJoin(lookups) : of([]);
          return people.pipe(map((found) => ({ result: response.data, found })));
        }),
        // caught here, inside switchMap: an error must not reach reload$, or the list stops reloading
        catchError(() => of(null)),
      );
  }

  // Next button
  nextPage(): void {
    if (!this.canGoForward()) return;
    this.pageNumber.update((value) => value + 1);
    this.load();
  }

  // Previous button
  prevPage(): void {
    if (!this.canGoBack()) return;
    this.pageNumber.update((value) => value - 1);
    this.load();
  }

  // the dropdowns and "Clear filters" only change the URL; ngOnInit reloads the list
  cityFilter(value: string): void {
    this.setFilters({ city: value || null });
  }

  categoryFilter(value: string): void {
    this.setFilters({ category: value || null });
  }

  clearAll(): void {
    this.setFilters({ category: null, city: null });
  }

  // change ?city / ?category in the URL (null removes it)
  private setFilters(queryParams: Params): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  // bookmark button: save or unsave a professional
  toggleSave(userId: string): void {
    if (this.saving().has(userId)) return; // ignore extra clicks while we wait for the server
    this.saving.update((ids) => new Set([...ids, userId]));
    this.saveError.set(null);

    const operation = this.savedIds().has(userId)
      ? this.userService.deleteConnection(userId)
      : this.userService.addConnection(userId);

    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        // reload the shared list so the contacts on other pages stay in sync
        this.network.load();
        this.finishSave(userId);
      },
      error: () => {
        this.saveError.set(userId);
        this.finishSave(userId);
      },
    });
  }

  // the save request is over
  private finishSave(id: string): void {
    this.saving.update((ids) => {
      const next = new Set(ids);
      next.delete(id);
      return next;
    });
  }

  // name or "View profile" clicked: open the popup and load the details
  openProfile(person: ProfessionalCard): void {
    this.selected.set(person);
    this.profile.set(null);
    this.ratingSummary.set(null);
    // stop the page behind the popup from scrolling
    this.previousOverflow = this.document.body.style.overflow;
    this.document.body.style.overflow = 'hidden';
    this.profileDialog().nativeElement.showModal();
    this.loadProfile();
    this.loadRatings();
  }

  // load the biography, services and cities (also the Retry button)
  loadProfile(): void {
    const person = this.selected();
    if (!person) return;
    this.profileRequest?.unsubscribe();
    this.profileLoading.set(true);
    this.profileFailed.set(false);
    this.profileRequest = this.userService
      .getProfessional(person.userId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.profile.set(response.data);
          this.profileLoading.set(false);
        },
        error: () => {
          this.profileFailed.set(true);
          this.profileLoading.set(false);
        },
      });
  }

  // load the average rating (also the Retry button)
  loadRatings(): void {
    const person = this.selected();
    if (!person) return;
    this.ratingsRequest?.unsubscribe();
    this.ratingsLoading.set(true);
    this.ratingsRequest = this.ratings
      .getSummary(person.userId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (summary) => {
          this.ratingSummary.set(summary);
          this.ratingsLoading.set(false);
        },
        error: () => {
          this.ratingSummary.set(null);
          this.ratingsLoading.set(false);
        },
      });
  }

  // the popup was closed (X button or Escape)
  dismissProfile(): void {
    this.profileRequest?.unsubscribe();
    this.ratingsRequest?.unsubscribe();
    this.selected.set(null);
    this.restoreScroll();
  }

  // let the page scroll again
  private restoreScroll(): void {
    if (this.previousOverflow === null) return;
    this.document.body.style.overflow = this.previousOverflow;
    this.previousOverflow = null;
  }
}
