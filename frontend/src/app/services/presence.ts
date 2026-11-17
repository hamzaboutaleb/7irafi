import { inject, Injectable } from '@angular/core';
import { catchError, EMPTY, exhaustMap, Subscription, timer } from 'rxjs';
import { UserService } from './user.js';
@Injectable({
  providedIn: 'root',
})
export class Presence {
  
  private timer : Subscription | null = null;
  private userService = inject(UserService);

  start() {
    if (this.timer == null) {
      // OLD (subscribe inside subscribe):
      // this.timer = timer(0, 45000).subscribe(
      //   () => this.userService.updateOnlineStatus().subscribe()
      // )

      // NEW (exhaustMap):
      // every 45s say "I'm online". exhaustMap skips a tick if the last ping hasn't answered,
      // and stop() now cancels a ping still in flight too (one subscription for everything).
      // catchError is inside exhaustMap: one failed ping is skipped instead of stopping the timer.
      this.timer = timer(0, 45000)
        .pipe(
          exhaustMap(() =>
            this.userService.updateOnlineStatus().pipe(catchError(() => EMPTY)),
          ),
        )
        .subscribe();
    }

  }

  stop () {
    if (this.timer != null) {
      this.timer.unsubscribe(); 
      this.timer = null;
    }

  }
}
