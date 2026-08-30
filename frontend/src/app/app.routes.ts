import { Routes } from '@angular/router';
import { Landing } from './pages/landing/landing';
import { HowItWorksPage } from './pages/how-it-works/how-it-works-page.js';
import { Login } from './pages/login/login.js';
import { Dashboard } from './pages/dashboard/dashboard.js';
import { authGuard } from './guards/auth-guard.js';
import { guestGuard } from './guards/guest-guard.js';
import { Register } from './pages/register/register.js';
import { Profile } from './pages/profile/profile.js';
import { ProfessionalProfile } from './pages/professional-profile/professional-profile.js';

export const routes: Routes = [
  { path: '', component: Landing },
  { path: 'how-it-works', component: HowItWorksPage },
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
];
