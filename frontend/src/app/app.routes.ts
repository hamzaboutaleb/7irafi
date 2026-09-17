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
import { Professionals } from './pages/professionals/professionals.js';
import { PrivacyPolicy } from './pages/privacy-policy/privacy-policy.js';
import { TermsOfService } from './pages/terms-of-service/terms-of-service.js';
import { Job } from './pages/jobs/job.js';
import type { CreateJob } from './components/create-job/create-job.js';

export const routes: Routes = [
  { path: '', component: Landing },
  { path: 'how-it-works', component: HowItWorksPage },
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
  {
    path: 'jobs/my',
    loadComponent: () => import('./pages/my-jobs/my-jobs.js').then((m) => m.MyJobs),
    canActivate: [authGuard],
  },
  {
    path: 'jobs/new',
    loadComponent: () => import('./components/create-job/create-job.js').then((module) => module.CreateJob),
    canActivate: [authGuard],
    canDeactivate: [
      (component: CreateJob, _route, _state, nextState) =>
        nextState.url === '/login' || component.canLeave(),
    ],
  },
  {
    path: 'applications',
    loadComponent: () => import('./pages/applications/applications.js').then((m) => m.Applications),
    canActivate: [authGuard],
  },
  {
    path: 'jobs/:jobId/applicants',
    loadComponent: () => import('./pages/job-applicants/job-applicants.js').then((m) => m.JobApplicants),
    canActivate: [authGuard],
  },
  {
    path: 'jobs',
    component: Job,
    canActivate: [authGuard],
  },
];
