import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { shortLinksApi } from '../api';
import {
  BucketGranularity,
  CreateShortLinkRequest,
  ShortLinkListQuery,
  UpdateShortLinkRequest,
} from '../types';

const SHORT_LINKS_KEY = ['short-links'] as const;

export function useShortLinks(query: ShortLinkListQuery = {}) {
  return useQuery({
    queryKey: [...SHORT_LINKS_KEY, 'list', query],
    queryFn: () => shortLinksApi.list(query),
    placeholderData: (prev) => prev,
  });
}

export function useShortLink(id: number | undefined) {
  return useQuery({
    queryKey: [...SHORT_LINKS_KEY, 'detail', id],
    queryFn: () => shortLinksApi.get(id as number),
    enabled: typeof id === 'number' && !Number.isNaN(id),
  });
}

export function useAnalytics(id: number | undefined, bucket: BucketGranularity) {
  return useQuery({
    queryKey: [...SHORT_LINKS_KEY, 'analytics', id, bucket],
    queryFn: () => shortLinksApi.analytics(id as number, bucket),
    enabled: typeof id === 'number' && !Number.isNaN(id),
  });
}

export function useCreateShortLink() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateShortLinkRequest) => shortLinksApi.create(body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: SHORT_LINKS_KEY });
    },
  });
}

export function useUpdateShortLink(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateShortLinkRequest) => shortLinksApi.update(id, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: SHORT_LINKS_KEY });
    },
  });
}

export function useDeleteShortLink() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => shortLinksApi.remove(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: SHORT_LINKS_KEY });
    },
  });
}
