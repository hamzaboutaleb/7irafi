import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { ApiSuccess } from '../types/api.js';
import { UserProfile, UpdateUserProfileRequest } from '../types/user.js';
import { ServiceCategory, ServiceCity } from '../types/professional-profile.js';

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private http = inject(HttpClient);

  getCurrentUser() {
    return this.http.get<ApiSuccess<UserProfile>>('users/me');
  }

  updateCurrentUser(request: UpdateUserProfileRequest) {
    return this.http.put<ApiSuccess<UserProfile>>('users/me', request);
  }

  getServiceCategories() {
    return this.http.get<ApiSuccess<ServiceCategory[]>>('users/categories');
  }

  getServiceCities() {
    return this.http.get<ApiSuccess<ServiceCity[]>>('users/cities');
  }
}
