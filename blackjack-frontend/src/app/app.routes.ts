import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login',    loadComponent: () => import('./pages/login/login').then(m => m.LoginComponent) },
  { path: 'register', loadComponent: () => import('./pages/register/register').then(m => m.RegisterComponent) },
  { path: 'lobby',    loadComponent: () => import('./pages/lobby/lobby').then(m => m.LobbyComponent) },
  { path: 'bet/:tableId',  loadComponent: () => import('./pages/bet/bet').then(m => m.BetComponent) },
  { path: 'game/:tableId', loadComponent: () => import('./pages/game/game').then(m => m.GameComponent) },
];
