import { Component, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth';

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
  private router = inject(Router);

  get isAuthenticated(): boolean {
    return this.authService.getToken() !== null;
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/']);
  }

}
