import { inject, Injectable } from '@angular/core';
import { Subscription, timer } from 'rxjs';
import { UserService } from './user.js';
@Injectable({
  providedIn: 'root',
})
export class Presence {
  
  private timer : Subscription | null = null;
  private userService = inject(UserService);

  start() {
    if (this.timer == null) {
      this.timer = timer(0, 45000).subscribe(
        () => this.userService.updateOnlineStatus().subscribe()
      )
    }

  }

  stop () {
    if (this.timer != null) {
      this.timer.unsubscribe(); 
      this.timer = null;
    }

  }
}
