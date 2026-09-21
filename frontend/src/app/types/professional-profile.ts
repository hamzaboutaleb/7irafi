export type ProfessionalOnboardingStatus = 'INCOMPLETE' | 'ACTIVE' | 'SUSPENDED';

export interface ServiceCategory {
  code: string;
  displayName: string;
  description: string;
}

export interface ServiceCity {
  code: string;
  displayName: string;
}

export interface ProfessionalProfile {
  userId: string;
  biography: string;
  onboardingStatus: ProfessionalOnboardingStatus;
  categories: ServiceCategory[];
  cities: ServiceCity[];
}

export interface CreateProfessionalProfileRequest {
  biography: string;
  cityCodes: string[];
  categoryCodes: string[];
}

export interface UpdateProfessionalProfileRequest {
  biography: string;
  cityCodes: string[];
  categoryCodes: string[];
}

export interface ProfessionalCard {
  userId: string;
  firstName: string;
  categories: ServiceCategory[];
  cities: ServiceCity[];
  online: boolean;
}

export interface PublicProfessionalProfile extends ProfessionalCard {
  biography: string;
}
