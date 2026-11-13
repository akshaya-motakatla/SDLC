export type TaskCategory = 'FRONTEND' | 'BACKEND' | 'DATABASE' | 'API' | 'SECURITY' | 'INTEGRATION' | 'OTHER'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED'
export type TaskReviewStatus = 'DRAFT' | 'APPROVED' | 'REJECTED'

export interface TaskRequirementReference {
  id: string
  code: string
  title: string
}

export interface TaskArchitectureReference {
  id: string
  title: string
  version: number | null
  architectureStyle: string | null
  status: 'DRAFT' | 'APPROVED' | 'REJECTED'
}

export interface DevelopmentTask {
  id: string
  projectId: string
  taskId: string
  title: string
  description: string
  category: TaskCategory
  priority: TaskPriority
  estimatedEffortHours: number | null
  dependencyIds: string[]
  dependencyTaskIds: string[]
  relatedRequirements: TaskRequirementReference[]
  architectureReferences: TaskArchitectureReference[]
  milestone: string | null
  acceptanceCriteria: string[]
  status: TaskStatus
  reviewStatus: TaskReviewStatus
  generationProvider: string
  demo: boolean
  createdAt: string
  updatedAt: string
}

export interface DevelopmentTaskUpdatePayload {
  title: string
  description: string
  category: TaskCategory
  priority: TaskPriority
  estimatedEffortHours: number
  milestone: string
  acceptanceCriteria: string[]
  relatedRequirementIds: string[]
  architectureReferenceIds: string[]
  dependencyIds: string[]
}

export interface DevelopmentTaskFilters {
  category?: TaskCategory | ''
  priority?: TaskPriority | ''
  status?: TaskStatus | ''
  milestone?: string
}

export interface DevelopmentTaskGenerationResult {
  provider: string
  demo: boolean
  tasks: DevelopmentTask[]
}
