import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { OnInit } from '@angular/core';
import { AuthService } from './services/auth.js';
import { Presence } from './services/presence.js';
import { ContactsRail } from './components/contacts-rail/contacts-rail.js';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ContactsRail],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  protected readonly title = signal('7irafy is here');
  private authService = inject(AuthService);
  private presence = inject(Presence);

  ngOnInit() {
    if (this.authService.getToken()) {
      this.presence.start();
    }
  }
}
