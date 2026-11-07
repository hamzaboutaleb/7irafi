import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { ApiSuccess, Page } from '../types/api.js';
import { UserProfile, PublicUserProfile, UpdateUserProfileRequest } from '../types/user.js';
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

  getPublicUserProfile(userId: string) {
    return this.http.get<ApiSuccess<PublicUserProfile>>(`users/${userId}/profile`);
  }

  updateCurrentUser(request: UpdateUserProfileRequest) {
    return this.http.put<ApiSuccess<UserProfile>>('users/me', request);
  }

  /**
   * Reports progress instead of returning a single response, so the page can
   * show how far an upload has got. The caller handles both event kinds.
   */
  uploadAvatar(file: File) {
    const body = new FormData();
    body.append('file', file);

    return this.http.post<ApiSuccess<UserProfile>>('users/me/avatar', body, {
      reportProgress: true,
      observe: 'events',
    });
  }

  deleteAvatar() {
    return this.http.delete<void>('users/me/avatar');
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

  getConnections() {
    return this.http.get<ApiSuccess<ProfessionalCard[]>>('users/me/connections');
  }

  addConnection(userId: string) {
    return this.http.post<void>('users/me/connections/' + userId, {});
  }

  deleteConnection(userId: string) {
    return this.http.delete<void>('users/me/connections/' + userId);
  }
}
