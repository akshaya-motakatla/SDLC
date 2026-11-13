import { apiRequest } from './apiClient'
import type {
  ArchitectureDocument,
  ArchitectureGenerationPayload,
  ArchitectureUpdatePayload,
} from '../types/architecture'

function isArchitectureDocument(value: unknown): value is ArchitectureDocument {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const document = value as Record<string, unknown>
  return typeof document.id === 'string'
    && typeof document.projectId === 'string'
    && Array.isArray(document.sourceRequirementIds)
    && document.sourceRequirementIds.every((id) => typeof id === 'string')
    && typeof document.title === 'string'
    && typeof document.content === 'string'
    && typeof document.architectureStyle === 'string'
    && typeof document.diagrams === 'object'
    && document.diagrams !== null
    && !Array.isArray(document.diagrams)
    && Object.values(document.diagrams).every((diagram) => typeof diagram === 'string')
    && ['DRAFT', 'APPROVED', 'REJECTED'].includes(String(document.status))
    && Number.isInteger(document.version)
    && typeof document.provider === 'string'
    && typeof document.demo === 'boolean'
    && typeof document.createdAt === 'string'
    && typeof document.updatedAt === 'string'
}

function projectPath(projectId: string) {
  return `/projects/${encodeURIComponent(projectId)}/architecture`
}

function parseDocument(value: unknown): ArchitectureDocument {
  if (!isArchitectureDocument(value)) {
    throw new Error('The architecture API returned data in an unexpected format.')
  }
  return value
}

function parseDocuments(value: unknown): ArchitectureDocument[] {
  if (!Array.isArray(value)) {
    throw new Error('The architecture API returned data in an unexpected format.')
  }
  return value.map(parseDocument)
}

export async function listArchitectureDocuments(projectId: string) {
  return parseDocuments(await apiRequest<unknown>(projectPath(projectId)))
}

export async function generateArchitecture(projectId: string, payload: ArchitectureGenerationPayload) {
  return parseDocument(await apiRequest<unknown>(`${projectPath(projectId)}/generate`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }))
}

export async function getArchitectureDocument(projectId: string, documentId: string) {
  return parseDocument(await apiRequest<unknown>(
    `${projectPath(projectId)}/${encodeURIComponent(documentId)}`,
  ))
}

export async function getArchitectureVersions(projectId: string, documentId: string) {
  return parseDocuments(await apiRequest<unknown>(
    `${projectPath(projectId)}/${encodeURIComponent(documentId)}/versions`,
  ))
}

export async function updateArchitectureDocument(
  projectId: string,
  documentId: string,
  payload: ArchitectureUpdatePayload,
) {
  return parseDocument(await apiRequest<unknown>(
    `${projectPath(projectId)}/${encodeURIComponent(documentId)}`,
    { method: 'PUT', body: JSON.stringify(payload) },
  ))
}

async function updateArchitectureStatus(projectId: string, documentId: string, action: 'approve' | 'reject') {
  return parseDocument(await apiRequest<unknown>(
    `${projectPath(projectId)}/${encodeURIComponent(documentId)}/${action}`,
    { method: 'POST' },
  ))
}

export function approveArchitecture(projectId: string, documentId: string) {
  return updateArchitectureStatus(projectId, documentId, 'approve')
}

export function rejectArchitecture(projectId: string, documentId: string) {
  return updateArchitectureStatus(projectId, documentId, 'reject')
}
