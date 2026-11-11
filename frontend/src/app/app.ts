import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { OnInit } from '@angular/core';
import { AuthService } from './services/auth.js';
import { Presence } from './services/presence.js';
import { ContactsRail } from './components/contacts-rail/contacts-rail.js';
import { NotificationService } from './services/notification.js';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ContactsRail],
  templateUrl: './app.html',
})
export class App implements OnInit {
  protected readonly title = signal('7irafy is here');
  private authService = inject(AuthService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private presence = inject(Presence);
  private notifications = inject(NotificationService);

  ngOnInit() {
    if (this.authService.getToken()) {
      this.presence.start();
    }

    // Reconnects after a login in this tab, and closes itself after a logout:
    // connect() is a no-op while the token is unchanged.
    this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.notifications.connect();
    });
  }
}
