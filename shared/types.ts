export interface User {
  userId: number;
  username: string;
  nickname: string;
  email: string;
  role: string;
  avatarUrl?: string;
}
export interface Hardware {
  id: number;
  type: string;
  brand: string;
  model: string;
  price: number;
  performanceScore: number;
  socketType: string;
  memoryType: string;
  formFactor: string;
  wattage: number;
  gpuLengthMm: number;
  coolerHeightMm: number;
  supportedFormFactors: string;
  specs: string;
  enabled: boolean;
  priceType?:string;
  priceLabel?:string;
  priceConditions?:string;
  productPrice?: number;
  productTitle?: string;
  productLink?: string;
  productImage?: string;
  skuId?: string;
}
export interface Build {
  id: number;
  ownerUserId: number;
  title: string;
  note: string;
  serialNumber: string;
  totalPrice: number;
  performanceScore?: number;
  source: string;
  favorite: boolean;
  parts: Hardware[];
  content: string;
  compatibilityStatus: string;
  compatibilityMessages: string[];
}
export interface Author {
  id: number;
  username: string;
  nickname: string;
  avatarUrl: string;
}
export interface Comment {
  id: number;
  author: Author;
  content: string;
  imageUrls: string[];
  likeCount: number;
  liked: boolean;
  createdAt: string;
  parentCommentId?: number;
  replyToNickname?: string;
}
export interface Post {
  id: number;
  author: Author;
  content: string;
  imageUrls: string[];
  likeCount: number;
  commentCount: number;
  favoriteCount: number;
  liked: boolean;
  favorite: boolean;
  createdAt: string;
  buildConfig?: Build;
}
export interface News {
  id: number;
  title: string;
  summary: string;
  content: string;
  tag: string;
  status: string;
  coverKey: string;
  coverUrl: string;
  createdAt: string;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  favoriteCount: number;
  liked: boolean;
  favorite: boolean;
}
export interface Reference {
  id: string;
  title: string;
  source: string;
  excerpt: string;
  updatedAt: string;
}
export interface ProductSelection {
  priceType?:string;priceLabel?:string;priceConditions?:string; hardwarePartId:number; title:string; price:number; image:string; link:string; skuId:string; matchedModel:string;
}
export interface Draft {
  title: string;
  note: string;
  partIds: number[];
  partSelections: ProductSelection[];
  source: string;
  parts: (Hardware & { selectedPrice: number })[];
  validation: {
    status: string;
    errors: string[];
    warnings: string[];
    quotedTotal: number;
    unpricedParts: string[];
  };
}
export interface Run {
  id: string;
  conversation_id: string;
  status: string;
  answer: string;
  draft?: Draft;
  references: Reference[];
  error?: string;
  created_at: string;
  partial_text?:string;
  event_cursor?:number;
  completion_required?:boolean;
  question?:{question:string;options?:string[]};
}
export const hardwareTypes: Record<string, string> = {
  CPU: "处理器",
  MOTHERBOARD: "主板",
  GPU: "显卡",
  MEMORY: "内存",
  STORAGE: "存储",
  PSU: "电源",
  CASE: "机箱",
  COOLER: "散热器",
};
export const runStatuses: Record<string, string> = {
  queued: "排队中",
  running: "执行中",
  needs_input: "等待补充",
  completed: "已完成",
  failed: "失败",
  interrupted: "已中断",
  cancelled: "已取消",
};
