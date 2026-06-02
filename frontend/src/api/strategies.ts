import { apiClient } from './client';
import { StrategyDescriptor } from '../types';

export const strategiesApi = {
  async list(): Promise<StrategyDescriptor[]> {
    const { data } = await apiClient.get<StrategyDescriptor[]>('/strategies');
    return data;
  },
};
