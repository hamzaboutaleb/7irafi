import { Component, inject, OnInit, signal } from '@angular/core';
import { NetworkService } from '../../services/network.js';
import { AuthService } from '../../services/auth.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

/**
 * A fixed rail of the people in your network, collapsed to avatars by default.
 *
 * Lives in the root layout rather than on a route, so it is present on every
 * page and its expanded/collapsed state survives navigation.
 *
 * This is where the chat panel will attach: clicking a contact will open a
 * conversation at the bottom of the screen.
 */
@Component({
  selector: 'app-contacts-rail',
  imports: [TranslatePipe],
  templateUrl: './contacts-rail.html',
})
export class ContactsRail implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly authService = inject(AuthService);

  readonly contacts = this.network.list;
  readonly expanded = signal(false);

  ngOnInit(): void {
    if (this.authService.getToken()) {
      this.network.load();
    }
  }

  get isAuthenticated(): boolean {
    return this.authService.getToken() !== null;
  }

  toggleExpanded(): void {
    this.expanded.update((value) => !value);
  }

  // Placeholder until chat exists. Kept so the click target and its wiring are
  // already in place; the panel attaches here.
  openConversation(userId: string): void {
    void userId;
  }
}
