import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { OnInit } from '@angular/core';
import { AuthService } from './services/auth.js';
import { Presence } from './services/presence.js';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit{
  protected readonly title = signal('7irafy is here');
  private authService = inject(AuthService);
  private presence = inject(Presence);

  ngOnInit() {
    if (this.authService.getToken())
    {
      this.presence.start();
    }
  }
}
