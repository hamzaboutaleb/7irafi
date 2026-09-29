export const environment = {
  production: true,
  apiUrl: '',
  // Empty: derived from the page's own host at runtime, since in production the
  // socket sits behind the same reverse proxy as the app.
  chatSocketUrl: '',
  notificationSocketUrl: '',
};
