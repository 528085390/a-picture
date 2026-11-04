import { request } from './request'
import type { CategoryAnalyze, SizeAnalyze, SpaceAnalyzeRequest, SpaceUsage, TagAnalyze, UserAnalyze, Space, SpaceUserAnalyzeRequest } from './types'
export const analyzeApi = {
  usage: (data: SpaceAnalyzeRequest) => request<SpaceUsage>('/space/analyze/usage', { method: 'POST', body: data }),
  category: (data: SpaceAnalyzeRequest) => request<CategoryAnalyze[]>('/space/analyze/category', { method: 'POST', body: data }),
  tag: (data: SpaceAnalyzeRequest) => request<TagAnalyze[]>('/space/analyze/tag', { method: 'POST', body: data }),
  size: (data: SpaceAnalyzeRequest) => request<SizeAnalyze[]>('/space/analyze/size', { method: 'POST', body: data }),
  user: (data: SpaceUserAnalyzeRequest) => request<UserAnalyze[]>('/space/analyze/user', { method: 'POST', body: data }),
  rank: (topN = 10) => request<Space[]>('/space/analyze/rank', { method: 'POST', body: { topN } }),
}
