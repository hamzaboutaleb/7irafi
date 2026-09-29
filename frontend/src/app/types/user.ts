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
