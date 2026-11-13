export type ArchitectureStatus = 'DRAFT' | 'APPROVED' | 'REJECTED'

export interface ArchitectureDocument {
  id: string
  projectId: string
  sourceRequirementIds: string[]
  title: string
  content: string
  architectureStyle: string
  diagrams: Record<string, string>
  status: ArchitectureStatus
  version: number
  provider: string
  demo: boolean
  createdAt: string
  updatedAt: string
}

export interface ArchitectureUpdatePayload {
  title: string
  architectureStyle: string
  content: string
  diagrams: Record<string, string>
}

export interface ArchitectureGenerationPayload {
  sourceRequirementIds: string[]
}
