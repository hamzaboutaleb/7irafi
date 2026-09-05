export interface ServiceStatus {
  name: string;
  status: string;
  instances: number;
  detail: string | null;
}

export interface PlatformStatus {
  status: string;
  checkedAt: string;
  services: ServiceStatus[];
}
