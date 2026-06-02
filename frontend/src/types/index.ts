/**
 * Type definitions mirroring the backend DTOs from `backend/API.md`.
 * Keep these in sync with the frozen API contract.
 */

export type LinkStatus = 'ACTIVE' | 'EXPIRED' | 'EXHAUSTED' | 'INACTIVE';

export type StrategyName =
  | 'RANDOM_BASE62'
  | 'HASH_TRUNC'
  | 'SEQUENTIAL'
  | 'CUSTOM_ALIAS'
  | (string & {});

export type ParameterType = 'string' | 'number' | 'boolean' | 'date';

export interface ParameterDescriptor {
  name: string;
  type: ParameterType;
  required: boolean;
  default?: string | number | boolean | null;
  min?: number;
  max?: number;
  pattern?: string;
  description?: string;
}

export interface StrategyDescriptor {
  name: StrategyName;
  displayName: string;
  description?: string;
  parameterSchema: ParameterDescriptor[];
}

export interface ShortLinkResponse {
  id: number;
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  strategy: StrategyName;
  expiresAt?: string | null;
  maxClicks?: number | null;
  clickCount: number;
  tags: string[];
  parameters: Record<string, unknown>;
  status: LinkStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ShortLinkListResponse {
  items: ShortLinkResponse[];
  page: number;
  size: number;
  total: number;
}

export interface CreateShortLinkRequest {
  originalUrl: string;
  strategy: StrategyName;
  alias?: string;
  expiresAt?: string | null;
  maxClicks?: number | null;
  tags?: string[];
  parameters?: Record<string, unknown>;
}

export interface UpdateShortLinkRequest {
  originalUrl?: string;
  expiresAt?: string | null;
  maxClicks?: number | null;
  tags?: string[];
}

export interface AnalyticsBucket {
  bucket: string;
  count: number;
}

export interface BreakdownEntry {
  value: string;
  count: number;
}

export interface AnalyticsResponse {
  shortLinkId: number;
  totalClicks: number;
  last24hClicks: number;
  uniqueReferers: number;
  series: AnalyticsBucket[];
  topReferers: BreakdownEntry[];
  topUserAgents: BreakdownEntry[];
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: ApiFieldError[];
}

/**
 * Normalized error shape thrown by the API client.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail?: string;
  readonly fieldErrors: ApiFieldError[];
  readonly type?: string;

  constructor(problem: ProblemDetail) {
    super(problem.detail || problem.title || `HTTP ${problem.status}`);
    this.name = 'ApiError';
    this.status = problem.status;
    this.title = problem.title || `HTTP ${problem.status}`;
    this.detail = problem.detail;
    this.fieldErrors = problem.errors ?? [];
    this.type = problem.type;
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((e) => e.field === field)?.message;
  }
}

export type BucketGranularity = 'hour' | 'day';

export interface ShortLinkListQuery {
  page?: number;
  size?: number;
  tag?: string;
  status?: LinkStatus;
}
