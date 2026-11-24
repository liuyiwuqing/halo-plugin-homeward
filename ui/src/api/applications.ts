import { axiosInstance } from '@halo-dev/api-client'
import type { ListResult } from '@/type'

export type ApplicationStatus = 'PENDING' | 'CONVERTING' | 'ACCEPTED' | 'REJECTED'
export interface CaseApplication {
  metadata: { name: string }
  spec: {
    personName: string
    missingDate?: string
    missingLocation: string
    description: string
    applicantName: string
    relationship: string
    contact: string
    sourceUrl?: string
    status: ApplicationStatus
    submittedAt: string
    consentAt: string
    handler?: string
    reviewNote?: string
    draftName?: string
  }
}
const base = '/apis/console.homeward.muyin.site/v1alpha1/applications'
export async function listApplications(params: { page: number; size: number; status?: string }) {
  return (await axiosInstance.get<ListResult<CaseApplication>>(`${base}/list`, { params })).data
}
export async function reviewApplication(name: string, action: 'accept' | 'reject', note: string) {
  return (
    await axiosInstance.post<CaseApplication>(`${base}/${encodeURIComponent(name)}/review`, {
      action,
      note,
    })
  ).data
}
