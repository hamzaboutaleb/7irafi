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

  // my account
  getCurrentUser() {
    return this.http.get<ApiSuccess<UserProfile>>('users/me');
  }

  // someone's public name and photo
  getPublicUserProfile(userId: string) {
    return this.http.get<ApiSuccess<PublicUserProfile>>(`users/${userId}/profile`);
  }

  // save my name
  updateCurrentUser(request: UpdateUserProfileRequest) {
    return this.http.put<ApiSuccess<UserProfile>>('users/me', request);
  }

  // upload photo, reports progress
  uploadAvatar(file: File) {
    const body = new FormData();
    body.append('file', file);

    return this.http.post<ApiSuccess<UserProfile>>('users/me/avatar', body, {
      reportProgress: true,
      observe: 'events',
    });
  }

  // remove my photo
  deleteAvatar() {
    return this.http.delete<void>('users/me/avatar');
  }

  // all service types
  getServiceCategories() {
    return this.http.get<ApiSuccess<ServiceCategory[]>>('users/categories');
  }

  // all cities
  getServiceCities() {
    return this.http.get<ApiSuccess<ServiceCity[]>>('users/cities');
  }

  // my pro profile
  getCurrentProfessionalProfile() {
    return this.http.get<ApiSuccess<ProfessionalProfile>>('users/me/professional-profile');
  }

  // become a pro
  createProfessionalProfile(request: CreateProfessionalProfileRequest) {
    return this.http.post<ApiSuccess<ProfessionalProfile>>(
      'users/me/professional-profile',
      request,
    );
  }

  // edit my pro profile
  updateProfessionalProfile(request: UpdateProfessionalProfileRequest) {
    return this.http.put<ApiSuccess<ProfessionalProfile>>('users/me/professional-profile', request);
  }

  // one page of professionals, filtered
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

  // one professional's details
  getProfessional(userId: string) {
    return this.http.get<ApiSuccess<PublicProfessionalProfile>>(`users/professionals/${userId}`);
  }

  // tell server I'm online
  updateOnlineStatus() {
    return this.http.post<void>('users/me/heartbeat', {});
  }

  // my saved contacts
  getConnections() {
    return this.http.get<ApiSuccess<ProfessionalCard[]>>('users/me/connections');
  }

  // save a contact
  addConnection(userId: string) {
    return this.http.post<void>('users/me/connections/' + userId, {});
  }

  // unsave a contact
  deleteConnection(userId: string) {
    return this.http.delete<void>('users/me/connections/' + userId);
  }
}
