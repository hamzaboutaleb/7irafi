import { Component, inject, OnInit, signal } from '@angular/core';
import { NetworkService } from '../../services/network.js';
import { AuthService } from '../../services/auth.js';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

// Saved contacts, on every page
@Component({
  selector: 'app-contacts-rail',
  imports: [TranslatePipe],
  templateUrl: './contacts-rail.html',
  styleUrl: './contacts-rail.css',
})
export class ContactsRail implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly authService = inject(AuthService);

  readonly contacts = this.network.list;
  readonly expanded = signal(false);

  // load contacts when signed in
  ngOnInit(): void {
    if (this.authService.getToken()) {
      this.network.load();
    }
  }

  get isAuthenticated(): boolean {
    return this.authService.getToken() !== null;
  }

  // open or close the rail
  toggleExpanded(): void {
    this.expanded.update((value) => !value);
  }

  // placeholder until chat exists
  openConversation(userId: string): void {
    void userId;
  }
}
