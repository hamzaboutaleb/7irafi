import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { PlatformStatus } from '../types/status.js';

@Injectable({
  providedIn: 'root',
})
export class StatusService {
  private http = inject(HttpClient);

  // Public endpoint on the gateway. Not wrapped in ApiResponse: it is served by
  // the gateway itself, not by user-service, and must stay reachable when the
  // services behind it are not.
  getPlatformStatus() {
    return this.http.get<PlatformStatus>('status');
  }
}
