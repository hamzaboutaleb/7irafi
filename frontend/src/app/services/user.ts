import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { ApiSuccess, Page } from '../types/api.js';
import { UserProfile, UpdateUserProfileRequest } from '../types/user.js';
import {
  ServiceCategory,
  ServiceCity,
  ProfessionalProfile,
  CreateProfessionalProfileRequest,
  UpdateProfessionalProfileRequest,
  ProfessionalCard,
  PublicProfessionalProfile,
} from '../types/professional-profile.js';

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

  getCurrentProfessionalProfile() {
    return this.http.get<ApiSuccess<ProfessionalProfile>>('users/me/professional-profile');
  }

  createProfessionalProfile(request: CreateProfessionalProfileRequest) {
    return this.http.post<ApiSuccess<ProfessionalProfile>>(
      'users/me/professional-profile',
      request,
    );
  }

  updateProfessionalProfile(request: UpdateProfessionalProfileRequest) {
    return this.http.put<ApiSuccess<ProfessionalProfile>>('users/me/professional-profile', request);
  }

  getProfessionals(
    page: number,
    size: number,
    cityCode: string | null,
    categoryCode: string | null,
  ) {
    let baseParams = new HttpParams();
    baseParams = baseParams.set('page', page).set('size', size);
    if (cityCode != null) {
      baseParams = baseParams.set('cityCode', cityCode);
    }
    if (categoryCode != null) {
      baseParams = baseParams.set('categoryCode', categoryCode);
    }

    return this.http.get<ApiSuccess<Page<ProfessionalCard>>>('users/professionals', {
      params: baseParams,
    });
  }

  getProfessional(userId: string) {
    return this.http.get<ApiSuccess<PublicProfessionalProfile>>(`users/professionals/${userId}`);
  }

  updateOnlineStatus() {
    return this.http.post<void>('users/me/heartbeat', {});
  }
}
