import { apiRequest } from './apiClient'
import type { Requirement, RequirementGenerationResult, RequirementUpdatePayload } from '../types/requirement'

function isRequirement(value: unknown): value is Requirement {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const requirement = value as Record<string, unknown>
  return typeof requirement.id === 'string'
    && typeof requirement.projectId === 'string'
    && typeof requirement.code === 'string'
    && typeof requirement.title === 'string'
    && typeof requirement.description === 'string'
    && ['FUNCTIONAL', 'NON_FUNCTIONAL'].includes(String(requirement.type))
    && ['HIGH', 'MEDIUM', 'LOW'].includes(String(requirement.priority))
    && Array.isArray(requirement.acceptanceCriteria)
    && requirement.acceptanceCriteria.every((criterion) => typeof criterion === 'string')
    && ['PENDING', 'APPROVED', 'REJECTED'].includes(String(requirement.status))
    && typeof requirement.source === 'string'
    && typeof requirement.createdAt === 'string'
    && typeof requirement.updatedAt === 'string'
}

function parseRequirements(value: unknown): Requirement[] {
  if (!Array.isArray(value) || !value.every(isRequirement)) {
    throw new Error('The requirements API returned data in an unexpected format.')
  }
  return value
}

function projectPath(projectId: string) {
  return `/projects/${encodeURIComponent(projectId)}/requirements`
}

export async function listRequirements(projectId: string) {
  return parseRequirements(await apiRequest<unknown>(projectPath(projectId)))
}

export async function generateRequirements(projectId: string) {
  const result = await apiRequest<unknown>(`${projectPath(projectId)}/generate`, { method: 'POST' })
  if (typeof result !== 'object' || result === null || Array.isArray(result)) {
    throw new Error('The requirements API returned data in an unexpected format.')
  }
  const generation = result as Record<string, unknown>
  if (typeof generation.provider !== 'string' || typeof generation.demo !== 'boolean') {
    throw new Error('The requirements API returned data in an unexpected format.')
  }
  return {
    provider: generation.provider,
    demo: generation.demo,
    requirements: parseRequirements(generation.requirements),
  } satisfies RequirementGenerationResult
}

export async function updateRequirement(
  projectId: string,
  requirementId: string,
  payload: RequirementUpdatePayload,
) {
  const result = await apiRequest<unknown>(
    `${projectPath(projectId)}/${encodeURIComponent(requirementId)}`,
    { method: 'PUT', body: JSON.stringify(payload) },
  )
  if (!isRequirement(result)) throw new Error('The requirements API returned data in an unexpected format.')
  return result
}

async function updateRequirementStatus(projectId: string, requirementId: string, status: 'approve' | 'reject') {
  const result = await apiRequest<unknown>(
    `${projectPath(projectId)}/${encodeURIComponent(requirementId)}/${status}`,
    { method: 'POST' },
  )
  if (!isRequirement(result)) throw new Error('The requirements API returned data in an unexpected format.')
  return result
}

export function approveRequirement(projectId: string, requirementId: string) {
  return updateRequirementStatus(projectId, requirementId, 'approve')
}

export function rejectRequirement(projectId: string, requirementId: string) {
  return updateRequirementStatus(projectId, requirementId, 'reject')
}

export function deleteRequirement(projectId: string, requirementId: string) {
  return apiRequest<void>(
    `${projectPath(projectId)}/${encodeURIComponent(requirementId)}`,
    { method: 'DELETE' },
  )
}
