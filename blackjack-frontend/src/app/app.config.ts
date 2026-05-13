import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    // Sin withFetch(): el cliente Http con XHR queda bien parcheado por Zone.js y la UI
    // actualiza al terminar las peticiones (withFetch a veces dejaba la vista colgada en "Conectando").
    provideHttpClient()
  ]
};
