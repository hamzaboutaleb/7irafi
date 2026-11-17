import { computed, inject, Injectable, signal } from '@angular/core';
import { UserService } from './user.js';
import { ProfessionalCard } from '../types/professional-profile.js';

// my contacts, shared app-wide
@Injectable({
  providedIn: 'root',
})
export class NetworkService {
  private userService = inject(UserService);

  private readonly connections = signal<ProfessionalCard[]>([]);
  private readonly loaded = signal(false);

  // the list, for the rail
  readonly list = this.connections.asReadonly();

  // ids, for bookmark buttons
  readonly savedIds = computed(() => new Set(this.connections().map((card) => card.userId)));

  readonly hasLoaded = this.loaded.asReadonly();

  // fetch contacts from server
  load(): void {
    this.userService.getConnections().subscribe({
      next: (response) => {
        this.connections.set(response.data);
        this.loaded.set(true);
      },
      error: () => {
        // empty rail, page still works
        this.connections.set([]);
        this.loaded.set(true);
      },
    });
  }

  // save a contact
  add(userId: string): void {
    this.userService.addConnection(userId).subscribe({
      // reload: server has full card
      next: () => this.load(),
    });
  }

  // unsave a contact
  remove(userId: string): void {
    this.userService.deleteConnection(userId).subscribe({
      next: () => {
        // drop it locally, no reload
        this.connections.set(this.connections().filter((card) => card.userId !== userId));
      },
    });
  }

  // save or unsave
  toggle(userId: string): void {
    if (this.savedIds().has(userId)) {
      this.remove(userId);
      return;
    }

    this.add(userId);
  }

  // empty the list (logout)
  clear(): void {
    this.connections.set([]);
    this.loaded.set(false);
  }
}
