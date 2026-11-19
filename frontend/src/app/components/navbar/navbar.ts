import { Component, DestroyRef, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { NavigationStart, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AuthService } from '../../services/auth';
import { TranslatePipe } from '../../pipes/translate-pipe.js';
import { LanguageSwitcher } from '../language-switcher/language-switcher.js';
import { ChatService } from '../../services/chat.js';
import { NotificationBell } from '../notification-bell/notification-bell.js';
import { NotificationService } from '../../services/notification.js';
import { LucideLayoutDashboard, LucideMessageSquare, LucideUserRound, LucideLogOut, LucideCircleHelp, LucideUsersRound, LucideBriefcaseBusiness, LucideChevronDown, LucideListFilter, LucidePlus, LucideClipboardList } from '@lucide/angular';

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive, TranslatePipe, LanguageSwitcher, NotificationBell, LucideLayoutDashboard, LucideMessageSquare, LucideUserRound, LucideLogOut, LucideCircleHelp, LucideUsersRound, LucideBriefcaseBusiness, LucideChevronDown, LucideListFilter, LucidePlus, LucideClipboardList],
  templateUrl: './navbar.html',
  styleUrls: [
    './navbar.css',
    './navbar-account.css',
    './navbar-mobile.css',
  ],
})
export class Navbar {
  readonly jobsOpen = signal(false);
  readonly mobileOpen = signal(false);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly mobileToggle = viewChild<ElementRef<HTMLButtonElement>>('mobileToggle');
  private readonly jobsMenu = viewChild<ElementRef<HTMLElement>>('jobsMenu');
  private readonly jobsToggle = viewChild<ElementRef<HTMLButtonElement>>('jobsToggle');

  @HostListener('document:click', ['$event'])
  closeMenusOutside(event: MouseEvent): void {
    const target = event.target as Node;
    if (!this.jobsMenu()?.nativeElement.contains(target)) this.jobsOpen.set(false);
    if (!this.element.nativeElement.contains(target)) this.closeMobile();
  }

  toggleMobile(): void {
    this.mobileOpen.update(open => !open);
    this.jobsOpen.set(false);
  }

  closeMobile(restoreFocus = false): void {
    if (!this.mobileOpen()) return;
    this.mobileOpen.set(false);
    if (restoreFocus) this.mobileToggle()?.nativeElement.focus();
  }

  @HostListener('document:keydown.escape', ['$event'])
  closeWithEscape(event: Event): void {
    if (this.mobileOpen()) {
      event.preventDefault();
      this.closeMobile(true);
    }
  }

  @HostListener('window:resize')
  closeOnDesktop(): void {
    // Reset the disclosure when crossing the same breakpoint used by the template.
    if (window.innerWidth >= 1280) this.closeMobile();
  }

  closeJobsWithEscape(): void {
    this.jobsOpen.set(false);
    this.jobsToggle()?.nativeElement.focus();
  }
  private authService = inject(AuthService);
  private chat = inject(ChatService);
  private notifications = inject(NotificationService);
  private router = inject(Router);

  constructor() {
    // Also covers navigation triggered elsewhere, rather than only menu clicks.
    this.router.events.pipe(
      filter(event => event instanceof NavigationStart),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe(() => {
      this.closeMobile();
      this.jobsOpen.set(false);
    });
  }

  get isAuthenticated(): boolean {
    return this.authService.getToken() !== null;
  }

  logout() {
    this.closeMobile();
    // Close the socket first: it is authenticated as this user, and must not
    // stay open for whoever signs in next in this tab.
    this.chat.disconnect();
    this.notifications.disconnect();
    this.authService.logout();
    this.router.navigate(['/']);
  }

}
