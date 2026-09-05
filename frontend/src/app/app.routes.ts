import { Routes } from '@angular/router';
import { Landing } from './pages/landing/landing';
import { HowItWorksPage } from './pages/how-it-works/how-it-works-page.js';
import { StatusPage } from './pages/status/status-page.js';
import { Login } from './pages/login/login.js';
import { Dashboard } from './pages/dashboard/dashboard.js';
import { authGuard } from './guards/auth-guard.js';
import { guestGuard } from './guards/guest-guard.js';
import { Register } from './pages/register/register.js';
import { Profile } from './pages/profile/profile.js';
import { ProfessionalProfile } from './pages/professional-profile/professional-profile.js';
import { Professionals } from './pages/professionals/professionals.js';
import { PrivacyPolicy } from './pages/privacy-policy/privacy-policy.js';
import { TermsOfService } from './pages/terms-of-service/terms-of-service.js';

export const routes: Routes = [
  { path: '', component: Landing },
  { path: 'how-it-works', component: HowItWorksPage },
  // Deliberately unguarded: a status page must be reachable when auth is down.
  { path: 'status', component: StatusPage },
  { path: 'privacy-policy', component: PrivacyPolicy },
  { path: 'terms-of-service', component: TermsOfService },
  {
    path: 'login',
    component: Login,
    canActivate: [guestGuard],
  },
  {
    path: 'dashboard',
    component: Dashboard,
    canActivate: [authGuard],
  },
  {
    path: 'register',
    component: Register,
    canActivate: [guestGuard],
  },
  {
    path: 'profile',
    component: Profile,
    canActivate: [authGuard],
  },
  {
    path: 'professional-profile',
    component: ProfessionalProfile,
    canActivate: [authGuard],
  },
  {
    path: 'professionals',
    component: Professionals,
    canActivate: [authGuard],
  },
];
