export type ID = string

export interface BaseResponse<T = unknown> { code: number; data: T; message: string }
export interface Page<T> { records: T[]; total: number; size: number; current: number; pages: number }
export interface PageRequest { current?: number; pageSize?: number; sortField?: string; sortOrder?: 'ascend' | 'descend'; searchText?: string; name?: string; category?: string }

export type UserRole = 'user' | 'admin'
export interface User {
  id: ID; userAccount: string; userName?: string; userAvatar?: string; userProfile?: string;
  userRole: UserRole; createTime?: string; editTime?: string; updateTime?: string
}
export type UserVO = Omit<User, 'userPassword'>
export type LoginUserVO = UserVO

export type PictureType = 'live' | 'static'
export interface Picture {
  id: ID; type?: PictureType; url?: string; previewUrl?: string; name?: string; introduction?: string;
  category?: string; tags?: string | string[]; picSize?: number; picWidth?: number; picHeight?: number;
  picScale?: number; picFormat?: string; userId?: ID; spaceId?: ID; createTime?: string; editTime?: string
}
export interface PictureVO extends Omit<Picture, 'tags'> { tags?: string[]; user?: UserVO }
export interface LivePictureVO { pictureVO: PictureVO; videoUrl?: string }

export type SpaceLevel = 0 | 1 | 2
export type SpaceType = 0 | 1
export type SpaceStatus = 0 | 1
export interface Space {
  id: ID; spaceName: string; spaceLevel: SpaceLevel; maxSize?: number; maxCount?: number;
  totalSize?: number; totalCount?: number; userId?: ID; spaceType: SpaceType; status?: SpaceStatus;
  createTime?: string; editTime?: string
}
export interface SpaceVO extends Space { user?: UserVO }
export type SpaceRole = 'viewer' | 'editor' | 'admin'
export interface SpaceUser { id: ID; spaceId: ID; userId: ID; spaceRole: SpaceRole; createTime?: string }
export interface SpaceUserVO extends SpaceUser { user?: UserVO; space?: SpaceVO }

export interface PictureUploadRequest { id?: ID }
export interface SpacePictureUploadRequest extends PictureUploadRequest { spaceId: ID; type?: PictureType }
export interface PictureEditRequest { id: ID; name?: string; introduction?: string; category?: string; tags?: string[] }
export interface SpacePictureBatchEditRequest { pictureIdList: ID[]; spaceId: ID; category?: string; tags?: string[]; nameRule?: string }

export interface SpaceAnalyzeRequest { spaceId?: ID; queryPublic?: boolean; queryAll?: boolean }
export interface SpaceUserAnalyzeRequest extends SpaceAnalyzeRequest { userId?: ID; timeDimension?: 'day' | 'week' | 'month' }
export interface SpaceUsage { usedSize: number; maxSize: number; sizeUsageRatio: number; usedCount: number; maxCount: number; countUsageRatio: number }
export interface CategoryAnalyze { category: string; count: number; totalSize: number }
export interface TagAnalyze { tag: string; count: number }
export interface SizeAnalyze { sizeRange: string; count: number }
export interface UserAnalyze { period: string; count: number }

export interface OutPaintingParams {
  angle?: number; outputRatio?: string; xScale?: number; yScale?: number;
  topOffset?: number; bottomOffset?: number; leftOffset?: number; rightOffset?: number;
  bestQuality?: boolean; limitImageSize?: boolean; addWatermark?: boolean
}
export interface OutPaintingOutput { taskId: string; taskStatus?: string; outputImageUrl?: string; message?: string; code?: string; submitTime?: string; scheduledTime?: string; endTime?: string }
export interface OutPaintingTaskResponse { taskId?: string; taskStatus?: string; outputImageUrl?: string; message?: string; code?: string; output?: OutPaintingOutput }
