import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { Navbar } from '../../components/navbar/navbar.js';
import { Footer } from '../../components/footer/footer.js';
import { DatePipe } from '@angular/common';
import { StatusService } from '../../services/status.js';
import { PlatformStatus } from '../../types/status.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-status-page',
  imports: [Navbar, Footer, DatePipe, TranslatePipe],
  templateUrl: './status-page.html',
})
export class StatusPage implements OnInit {
  private readonly statusService = inject(StatusService);
  private readonly destroyRef = inject(DestroyRef);

  readonly status = signal<PlatformStatus | null>(null);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    // Refreshes every 15s. A status page showing a stale snapshot during an
    // incident is worse than no status page.
    timer(0, 15000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }

  private load(): void {
    this.statusService.getPlatformStatus().subscribe({
      next: (response) => {
        this.status.set(response);
        this.errorMessage.set(null);
        this.isLoading.set(false);
      },
      error: () => {
        // The gateway itself is unreachable. Keep the last known snapshot on
        // screen and say so, rather than blanking the page.
        this.errorMessage.set('The API gateway is unreachable. Nothing below can be verified.');
        this.isLoading.set(false);
      },
    });
  }
}
