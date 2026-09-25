export interface AppNotification {
  id: string;
  type: string;
  title: string;
  body: string;
  targetUrl: string | null;
  createdAt: string;
  read: boolean;
}
