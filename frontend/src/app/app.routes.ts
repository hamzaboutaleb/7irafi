import { Routes } from '@angular/router';
import { Landing } from './pages/landing/landing';
import { Login } from './pages/login/login.js';
import { Dashboard } from './pages/dashboard/dashboard.js';
import { authGuard } from './guards/auth-guard.js';
import { guestGuard } from './guards/guest-guard.js';
import { Register } from './pages/register/register.js';

export const routes: Routes = [
  { path: '', component: Landing },
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
];
