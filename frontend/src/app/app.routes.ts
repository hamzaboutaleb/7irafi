import { Routes } from '@angular/router';
import { Landing } from './pages/landing/landing';
import { Login } from './pages/login/login.js';
import { Home } from './pages/home/home.js';
import { authGuard } from './guards/auth-guard.js';
import { guestGuard } from './guards/guest-guard.js';

export const routes: Routes = [
  { path: '', component: Landing },
  {
    path: 'login',
    component: Login,
    canActivate: [guestGuard],
  },
  {
    path: 'home',
    component: Home,
    canActivate: [authGuard],
  },
];
