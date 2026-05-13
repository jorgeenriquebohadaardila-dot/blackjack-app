/**
 * `ng serve` con proxy: peticiones van a /api y /ws en el mismo origen (4200)
 * y el proxy las reenvía al backend en 8080 (evita CORS y URLs cruzadas).
 */
export const environment = {
  production: false,
  apiOrigin: '',
};
