export type CaseStatus = 'DRAFT' | 'PUBLISHED' | 'FOUND' | 'ARCHIVED'

export type ClueStatus = 'PENDING' | 'CONTACTED' | 'INVALID' | 'RESOLVED'

export interface ClueProcessingRecord {
  previousStatus?: ClueStatus
  status?: ClueStatus
  handler?: string
  processNote?: string
  processedAt?: string
  legacy?: boolean
}

export interface MissingPersonCaseVO {
  name?: string
  slug?: string
  title?: string
  personName?: string
  gender?: string
  birthDate?: string
  missingDate?: string
  missingAge?: number
  currentAge?: number
  missingLocation?: string
  hometown?: string
  coverImage?: string
  physicalFeatures?: string
  description?: string
  contactName?: string
  contactPhone?: string
  sourceName?: string
  sourceUrl?: string
  status?: CaseStatus
  priority?: number
  publishedAt?: string
  foundAt?: string
}

export interface ClueReportVO {
  name?: string
  caseSlug?: string
  caseTitle?: string
  submitterName?: string
  contact?: string
  content?: string
  location?: string
  ipHash?: string
  userAgent?: string
  status?: ClueStatus
  handler?: string
  processNote?: string
  submittedAt?: string
  updatedAt?: string
  processingHistory?: ClueProcessingRecord[]
}

export interface ListResult<T> {
  page: number
  size: number
  total: number
  items: T[]
}

export interface DashboardStats {
  totalCases: number
  publishedCases: number
  foundCases: number
  pendingClues: number
}

export interface MissingPersonCasePayload {
  metadata?: {
    name?: string
  }
  spec: Partial<MissingPersonCaseVO>
}

export interface CaseImportRequest {
  sourceUrl?: string
  pageContent?: string
  ai?: boolean
  modelName?: string
}

export interface CaseImportPreview {
  caseData: MissingPersonCaseVO
  extractedFieldCount: number
  warnings: string[]
  aiGenerated?: boolean
  aiAvailable?: boolean
  aiRecommended?: boolean
  parser?: string
  modelName?: string
}

export interface CaseImportBatchRequest {
  items: CaseImportRequest[]
  modelName?: string
}

export interface CaseImportBatchItem {
  index: number
  sourceUrl?: string
  preview?: CaseImportPreview
  error?: string
}

export interface CaseImportBatchPreview {
  items: CaseImportBatchItem[]
  succeeded: number
  failed: number
}
