import { useQuery } from '@tanstack/react-query';
import { strategiesApi } from '../api';

export function useStrategies() {
  return useQuery({
    queryKey: ['strategies'],
    queryFn: () => strategiesApi.list(),
    staleTime: 5 * 60 * 1000,
  });
}
