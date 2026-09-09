import { computed, inject, Injectable, signal } from '@angular/core';
import { UserService } from './user.js';
import { ProfessionalCard } from '../types/professional-profile.js';

/**
 * The signed-in user's network, held in one place.
 *
 * Both the directory (bookmark buttons) and the contacts rail read this. If each
 * kept its own copy, saving someone on the directory would leave the rail stale
 * until a reload.
 */
@Injectable({
  providedIn: 'root',
})
export class NetworkService {
  private userService = inject(UserService);

  private readonly connections = signal<ProfessionalCard[]>([]);
  private readonly loaded = signal(false);

  /** The saved professionals, for the rail. */
  readonly list = this.connections.asReadonly();

  /** Just the ids, for "is this person saved?" checks on directory cards. */
  readonly savedIds = computed(() => new Set(this.connections().map((card) => card.userId)));

  readonly hasLoaded = this.loaded.asReadonly();

  load(): void {
    this.userService.getConnections().subscribe({
      next: (response) => {
        this.connections.set(response.data);
        this.loaded.set(true);
      },
      error: () => {
        // Swallowed on purpose: every page that shows the rail must still work.
        // The cost is an empty rail until the next load.
        this.connections.set([]);
        this.loaded.set(true);
      },
    });
  }

  add(userId: string): void {
    this.userService.addConnection(userId).subscribe({
      // Reload rather than guessing: the card the rail needs (name, categories,
      // online) is not on the directory card we clicked, so the server is the
      // only place that has it.
      next: () => this.load(),
    });
  }

  remove(userId: string): void {
    this.userService.deleteConnection(userId).subscribe({
      next: () => {
        // Removal needs no server round trip to render — drop it locally and
        // let the next load reconcile.
        this.connections.set(this.connections().filter((card) => card.userId !== userId));
      },
    });
  }

  toggle(userId: string): void {
    if (this.savedIds().has(userId)) {
      this.remove(userId);
      return;
    }

    this.add(userId);
  }

  clear(): void {
    this.connections.set([]);
    this.loaded.set(false);
  }
}
