import { Routes } from '@angular/router';
import { Landing } from './pages/landing/landing';
import { HowItWorksPage } from './pages/how-it-works/how-it-works-page.js';
import { StatusPage } from './pages/status/status-page.js';
import { Login } from './pages/login/login.js';
import { authGuard } from './guards/auth-guard.js';
import { guestGuard } from './guards/guest-guard.js';
import { Register } from './pages/register/register.js';
import { Profile } from './pages/profile/profile.js';
import { ProfessionalProfile } from './pages/professional-profile/professional-profile.js';
import { PrivacyPolicy } from './pages/privacy-policy/privacy-policy.js';
import { TermsOfService } from './pages/terms-of-service/terms-of-service.js';
import { Job } from './pages/jobs/job.js';
import type { CreateJob } from './components/create-job/create-job.js';

export const routes: Routes = [
  { path: '', title: 'pageTitle.home', component: Landing },
  { path: 'how-it-works', title: 'pageTitle.howItWorks', component: HowItWorksPage },
  // Deliberately unguarded: a status page must be reachable when auth is down.
  { path: 'status', title: 'pageTitle.status', component: StatusPage },
  { path: 'privacy-policy', title: 'pageTitle.privacy', component: PrivacyPolicy },
  { path: 'terms-of-service', title: 'pageTitle.terms', component: TermsOfService },
  {
    path: 'login', title: 'pageTitle.login',
    component: Login,
    canActivate: [guestGuard],
  },
  {
    path: 'dashboard', title: 'pageTitle.dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard.js').then((m) => m.Dashboard),
    canActivate: [authGuard],
  },
  {
    path: 'register', title: 'pageTitle.register',
    component: Register,
    canActivate: [guestGuard],
  },
  {
    path: 'profile', title: 'pageTitle.profile',
    component: Profile,
    canActivate: [authGuard],
  },
  {
    path: 'professional-profile', title: 'pageTitle.professionalProfile',
    component: ProfessionalProfile,
    canActivate: [authGuard],
  },
  {
    path: 'professionals', title: 'pageTitle.professionals',
    loadComponent: () => import('./pages/professionals/professionals.js').then((m) => m.Professionals),
    canActivate: [authGuard],
  },
  {
    path: 'jobs/my', title: 'pageTitle.myJobs',
    loadComponent: () => import('./pages/my-jobs/my-jobs.js').then((m) => m.MyJobs),
    canActivate: [authGuard],
  },
  {
    path: 'jobs/new', title: 'pageTitle.postJob',
    loadComponent: () => import('./components/create-job/create-job.js').then((module) => module.CreateJob),
    canActivate: [authGuard],
    canDeactivate: [
      (component: CreateJob, _route, _state, nextState) =>
        nextState.url === '/login' || component.canLeave(),
    ],
  },
  {
    path: 'applications', title: 'pageTitle.applications',
    loadComponent: () => import('./pages/applications/applications.js').then((m) => m.Applications),
    canActivate: [authGuard],
  },
  {
    path: 'jobs/:jobId/applicants', title: 'pageTitle.applicants',
    loadComponent: () => import('./pages/job-applicants/job-applicants.js').then((m) => m.JobApplicants),
    canActivate: [authGuard],
  },
  {
    path: 'jobs', title: 'pageTitle.jobs',
    component: Job,
    canActivate: [authGuard],
  },
  {
    path: 'conversations', title: 'pageTitle.messages',
    loadComponent: () => import('./pages/conversations/conversations.js').then((m) => m.Conversations),
    canActivate: [authGuard],
    children: [{
      path: ':id', title: 'pageTitle.messages',
      loadComponent: () => import('./pages/conversation/conversation.js').then((m) => m.Conversation),
    }],
  },
];
