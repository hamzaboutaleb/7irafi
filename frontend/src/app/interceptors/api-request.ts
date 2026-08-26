export function isApiRequest(url: string): boolean {
  return !url.startsWith('/') && !url.startsWith('http://') && !url.startsWith('https://');
}
