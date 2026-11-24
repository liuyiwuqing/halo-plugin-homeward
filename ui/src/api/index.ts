import { axiosInstance } from '@halo-dev/api-client'
import type {
  CaseImportBatchPreview,
  CaseImportBatchRequest,
  CaseImportPreview,
  CaseImportRequest,
  ClueReportVO,
  ClueStatus,
  DashboardStats,
  ListResult,
  MissingPersonCasePayload,
  MissingPersonCaseVO,
} from '@/type'

const CONSOLE_BASE = '/apis/console.homeward.muyin.site/v1alpha1'

export async function listCases(params: Record<string, unknown>) {
  const { data } = await axiosInstance.get<ListResult<MissingPersonCaseVO>>(
    `${CONSOLE_BASE}/cases/list`,
    { params },
  )
  return data
}

export async function saveCase(payload: MissingPersonCasePayload) {
  const url = payload.metadata?.name ? '/cases/update' : '/cases/create'
  const { data } = await axiosInstance.post<MissingPersonCaseVO>(`${CONSOLE_BASE}${url}`, payload)
  return data
}

export async function previewCaseImport(payload: CaseImportRequest) {
  const { data } = await axiosInstance.post<CaseImportPreview>(
    `${CONSOLE_BASE}/cases/import/preview`,
    payload,
  )
  return data
}

export async function previewCaseImportDocument(file: File, options?: CaseImportRequest) {
  const formData = new FormData()
  formData.append('file', file)
  if (options?.modelName) formData.append('modelName', options.modelName)
  if (options?.sourceUrl) formData.append('sourceUrl', options.sourceUrl)
  const { data } = await axiosInstance.post<CaseImportPreview>(
    `${CONSOLE_BASE}/cases/import/preview/document`,
    formData,
  )
  return data
}

export async function previewCaseImportBatch(payload: CaseImportBatchRequest) {
  const { data } = await axiosInstance.post<CaseImportBatchPreview>(
    `${CONSOLE_BASE}/cases/import/batch/preview`,
    payload,
  )
  return data
}

export async function previewCaseImportDocuments(files: File[], options?: CaseImportRequest) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  if (options?.modelName) formData.append('modelName', options.modelName)
  if (options?.sourceUrl) formData.append('sourceUrl', options.sourceUrl)
  const { data } = await axiosInstance.post<CaseImportBatchPreview>(
    `${CONSOLE_BASE}/cases/import/batch/preview/documents`,
    formData,
  )
  return data
}

export async function updateCaseStatus(name: string, action: 'publish' | 'archive' | 'mark-found') {
  const { data } = await axiosInstance.post<MissingPersonCaseVO>(
    `${CONSOLE_BASE}/cases/${name}/${action}`,
  )
  return data
}

export async function deleteCase(name: string) {
  const { data } = await axiosInstance.delete<MissingPersonCaseVO>(
    `${CONSOLE_BASE}/cases/${name}/delete`,
  )
  return data
}

export async function listClues(params: Record<string, unknown>) {
  const { data } = await axiosInstance.get<ListResult<ClueReportVO>>(`${CONSOLE_BASE}/clues/list`, {
    params,
  })
  return data
}

export async function updateClueStatus(
  name: string,
  payload: { status: ClueStatus; processNote?: string },
) {
  const { data } = await axiosInstance.post<ClueReportVO>(
    `${CONSOLE_BASE}/clues/${name}/status`,
    payload,
  )
  return data
}

export async function deleteClue(name: string) {
  const { data } = await axiosInstance.delete<ClueReportVO>(`${CONSOLE_BASE}/clues/${name}/delete`)
  return data
}

export async function getDashboardStats() {
  const { data } = await axiosInstance.get<DashboardStats>(`${CONSOLE_BASE}/dashboard/stats`)
  return data
}
