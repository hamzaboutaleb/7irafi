import { ServiceCategory, ServiceCity } from './professional-profile.js';

/** Profile fields available to other signed-in users, including job owners. */
export interface PublicUserProfile {
  id: string;
  firstName: string;
  lastName: string;
  avatarUrl: string | null;
  categories: ServiceCategory[];
  cities: ServiceCity[];
}

export interface UserProfile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  createdAt: string;
  updatedAt: string;
  /** Signed link that expires; null when no avatar has been uploaded. */
  avatarUrl: string | null;
}

export interface UpdateUserProfileRequest {
  firstName: string;
  lastName: string;
}
