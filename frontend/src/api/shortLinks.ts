import { apiClient } from './client';
import {
  AnalyticsResponse,
  BucketGranularity,
  CreateShortLinkRequest,
  ShortLinkListQuery,
  ShortLinkListResponse,
  ShortLinkResponse,
  UpdateShortLinkRequest,
} from '../types';

export const shortLinksApi = {
  async list(query: ShortLinkListQuery = {}): Promise<ShortLinkListResponse> {
    const params: Record<string, string | number> = {};
    if (query.page !== undefined) params.page = query.page;
    if (query.size !== undefined) params.size = query.size;
    if (query.tag) params.tag = query.tag;
    if (query.status) params.status = query.status;
    const { data } = await apiClient.get<ShortLinkListResponse>('/short-links', { params });
    return data;
  },

  async get(id: number): Promise<ShortLinkResponse> {
    const { data } = await apiClient.get<ShortLinkResponse>(`/short-links/${id}`);
    return data;
  },

  async create(body: CreateShortLinkRequest): Promise<ShortLinkResponse> {
    const { data } = await apiClient.post<ShortLinkResponse>('/short-links', body);
    return data;
  },

  async update(id: number, body: UpdateShortLinkRequest): Promise<ShortLinkResponse> {
    const { data } = await apiClient.put<ShortLinkResponse>(`/short-links/${id}`, body);
    return data;
  },

  async remove(id: number): Promise<void> {
    await apiClient.delete(`/short-links/${id}`);
  },

  async analytics(
    id: number,
    bucket: BucketGranularity = 'hour',
    from?: string,
    to?: string,
  ): Promise<AnalyticsResponse> {
    const params: Record<string, string> = { bucket };
    if (from) params.from = from;
    if (to) params.to = to;
    const { data } = await apiClient.get<AnalyticsResponse>(
      `/short-links/${id}/analytics`,
      { params },
    );
    return data;
  },

  /**
   * Fetch the QR PNG as a blob. Caller is responsible for `URL.createObjectURL`
   * and revoking it on cleanup.
   */
  async qrBlob(id: number, size = 512): Promise<Blob> {
    const response = await apiClient.get(`/short-links/${id}/qr`, {
      params: { size },
      responseType: 'blob',
    });
    return response.data as Blob;
  },
};
