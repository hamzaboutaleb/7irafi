/** Keep post-login navigation inside this application. */
export function authDestination(returnUrl: string | null): string {
  return returnUrl?.startsWith('/') && !returnUrl.startsWith('//') && !returnUrl.includes('\\')
    ? returnUrl : '/';
}
