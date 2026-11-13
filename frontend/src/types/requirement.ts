export type RequirementType = 'FUNCTIONAL' | 'NON_FUNCTIONAL'
export type RequirementPriority = 'HIGH' | 'MEDIUM' | 'LOW'
export type RequirementStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface Requirement {
  id: string
  projectId: string
  code: string
  title: string
  description: string
  type: RequirementType
  priority: RequirementPriority
  acceptanceCriteria: string[]
  status: RequirementStatus
  source: string
  createdAt: string
  updatedAt: string
}

export interface RequirementUpdatePayload {
  title: string
  description: string
  type: RequirementType
  priority: RequirementPriority
  acceptanceCriteria: string[]
}

export interface RequirementGenerationResult {
  provider: string
  demo: boolean
  requirements: Requirement[]
}
