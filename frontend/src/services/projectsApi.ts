import { apiRequest } from './apiClient'
import type { Project, ProjectPayload } from '../types/project'

function isProject(value: unknown): value is Project {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const project = value as Record<string, unknown>
  return typeof project.id === 'string'
    && typeof project.name === 'string'
    && typeof project.description === 'string'
    && (typeof project.objectives === 'string' || project.objectives === null)
    && typeof project.domain === 'string'
    && typeof project.preferredStack === 'string'
    && typeof project.status === 'string'
    && typeof project.createdAt === 'string'
    && typeof project.updatedAt === 'string'
}

function parseProject(value: unknown): Project {
  if (!isProject(value)) {
    throw new Error('The project API returned data in an unexpected format.')
  }
  return value
}

export async function listProjects() {
  const response = await apiRequest<unknown>('/projects')
  if (!Array.isArray(response) || !response.every(isProject)) {
    throw new Error('The project API returned data in an unexpected format.')
  }
  return response
}

export async function createProject(project: ProjectPayload) {
  const response = await apiRequest<unknown>('/projects', {
    method: 'POST',
    body: JSON.stringify(project),
  })
  return parseProject(response)
}

export async function updateProject(id: string, project: ProjectPayload) {
  const response = await apiRequest<unknown>(`/projects/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(project),
  })
  return parseProject(response)
}

export function deleteProject(id: string) {
  return apiRequest<void>(`/projects/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
