import { Component, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth';
import { ChatService } from '../../services/chat.js';
import { NotificationService } from '../../services/notification.js';

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './navbar.html',
  styleUrl: './navbar.css',
})
export class Navbar {
  readonly jobsOpen = signal(false);
  private readonly jobsMenu = viewChild<ElementRef<HTMLElement>>('jobsMenu');
  private readonly jobsToggle = viewChild<ElementRef<HTMLButtonElement>>('jobsToggle');

  @HostListener('document:click', ['$event'])
  closeJobsOutside(event: MouseEvent): void {
    if (!this.jobsMenu()?.nativeElement.contains(event.target as Node)) this.jobsOpen.set(false);
  }

  closeJobsWithEscape(): void {
    this.jobsOpen.set(false);
    this.jobsToggle()?.nativeElement.focus();
  }
  private authService = inject(AuthService);
  private chat = inject(ChatService);
  private notifications = inject(NotificationService);
  private router = inject(Router);

  /** Unread notifications, shown as a red count on the Profile link. */
  readonly unread = this.notifications.unread;

  get isAuthenticated(): boolean {
    return this.authService.getToken() !== null;
  }

  logout() {
    // Close the socket first: it is authenticated as this user, and must not
    // stay open for whoever signs in next in this tab.
    this.chat.disconnect();
    this.notifications.disconnect();
    this.authService.logout();
    this.router.navigate(['/']);
  }

}
