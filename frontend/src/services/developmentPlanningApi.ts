import { apiRequest } from './apiClient'
import type {
  DevelopmentTask,
  DevelopmentTaskFilters,
  DevelopmentTaskGenerationResult,
  DevelopmentTaskUpdatePayload,
  TaskStatus,
} from '../types/developmentTask'

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string')
}

function isRequirementReference(value: unknown) {
  return isObject(value)
    && typeof value.id === 'string'
    && typeof value.code === 'string'
    && typeof value.title === 'string'
}

function isArchitectureReference(value: unknown) {
  return isObject(value)
    && typeof value.id === 'string'
    && typeof value.title === 'string'
    && (typeof value.version === 'number' || value.version === null)
    && (typeof value.architectureStyle === 'string' || value.architectureStyle === null)
    && ['DRAFT', 'APPROVED', 'REJECTED'].includes(String(value.status))
}

function isDevelopmentTask(value: unknown): value is DevelopmentTask {
  if (!isObject(value)) return false
  return typeof value.id === 'string'
    && typeof value.projectId === 'string'
    && typeof value.taskId === 'string'
    && typeof value.title === 'string'
    && typeof value.description === 'string'
    && ['FRONTEND', 'BACKEND', 'DATABASE', 'API', 'SECURITY', 'INTEGRATION', 'OTHER'].includes(String(value.category))
    && ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(String(value.priority))
    && (typeof value.estimatedEffortHours === 'number' || value.estimatedEffortHours === null)
    && isStringArray(value.dependencyIds)
    && isStringArray(value.dependencyTaskIds)
    && Array.isArray(value.relatedRequirements)
    && value.relatedRequirements.every(isRequirementReference)
    && Array.isArray(value.architectureReferences)
    && value.architectureReferences.every(isArchitectureReference)
    && (typeof value.milestone === 'string' || value.milestone === null)
    && isStringArray(value.acceptanceCriteria)
    && ['TODO', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED'].includes(String(value.status))
    && ['DRAFT', 'APPROVED', 'REJECTED'].includes(String(value.reviewStatus))
    && typeof value.generationProvider === 'string'
    && typeof value.demo === 'boolean'
    && typeof value.createdAt === 'string'
    && typeof value.updatedAt === 'string'
}

function parseTask(value: unknown): DevelopmentTask {
  if (!isDevelopmentTask(value)) {
    throw new Error('The development planning API returned data in an unexpected format.')
  }
  return value
}

function parseTasks(value: unknown): DevelopmentTask[] {
  if (!Array.isArray(value)) {
    throw new Error('The development planning API returned data in an unexpected format.')
  }
  return value.map(parseTask)
}

function projectPath(projectId: string) {
  return `/projects/${encodeURIComponent(projectId)}/development-tasks`
}

export async function listDevelopmentTasks(projectId: string, filters: DevelopmentTaskFilters = {}) {
  const query = new URLSearchParams()
  if (filters.category) query.set('category', filters.category)
  if (filters.priority) query.set('priority', filters.priority)
  if (filters.status) query.set('status', filters.status)
  if (filters.milestone?.trim()) query.set('milestone', filters.milestone.trim())
  const suffix = query.size ? `?${query.toString()}` : ''
  return parseTasks(await apiRequest<unknown>(`${projectPath(projectId)}${suffix}`))
}

export async function generateDevelopmentTasks(projectId: string) {
  const value: unknown = await apiRequest(`${projectPath(projectId)}/generate`, { method: 'POST' })
  if (!isObject(value) || typeof value.provider !== 'string' || typeof value.demo !== 'boolean') {
    throw new Error('The development planning API returned data in an unexpected format.')
  }
  return {
    provider: value.provider,
    demo: value.demo,
    tasks: parseTasks(value.tasks),
  } satisfies DevelopmentTaskGenerationResult
}

export async function getDevelopmentTask(projectId: string, taskId: string) {
  return parseTask(await apiRequest<unknown>(`${projectPath(projectId)}/${encodeURIComponent(taskId)}`))
}

export async function updateDevelopmentTask(
  projectId: string,
  taskId: string,
  payload: DevelopmentTaskUpdatePayload,
) {
  return parseTask(await apiRequest<unknown>(`${projectPath(projectId)}/${encodeURIComponent(taskId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  }))
}

export async function updateDevelopmentTaskStatus(projectId: string, taskId: string, status: TaskStatus) {
  return parseTask(await apiRequest<unknown>(`${projectPath(projectId)}/${encodeURIComponent(taskId)}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  }))
}

async function updateReviewStatus(projectId: string, taskId: string, action: 'approve' | 'reject') {
  return parseTask(await apiRequest<unknown>(
    `${projectPath(projectId)}/${encodeURIComponent(taskId)}/${action}`,
    { method: 'POST' },
  ))
}

export function approveDevelopmentTask(projectId: string, taskId: string) {
  return updateReviewStatus(projectId, taskId, 'approve')
}

export function rejectDevelopmentTask(projectId: string, taskId: string) {
  return updateReviewStatus(projectId, taskId, 'reject')
}

export async function deleteDevelopmentTask(projectId: string, taskId: string) {
  await apiRequest<void>(`${projectPath(projectId)}/${encodeURIComponent(taskId)}`, { method: 'DELETE' })
}
