import { apiRequest } from './apiClient'
import type { AuthenticatedUser, LoginPayload, RegisterPayload } from '../types/auth'

function isAuthenticatedUser(value: unknown): value is AuthenticatedUser {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const user = value as Record<string, unknown>
  return typeof user.id === 'string'
    && typeof user.username === 'string'
    && typeof user.email === 'string'
    && typeof user.firstName === 'string'
    && typeof user.lastName === 'string'
}

function parseAuthenticatedUser(value: unknown) {
  if (!isAuthenticatedUser(value)) {
    throw new Error('The authentication API returned data in an unexpected format.')
  }
  return value
}

export async function getCurrentUser() {
  return parseAuthenticatedUser(await apiRequest<unknown>('/auth/me'))
}

export async function login(payload: LoginPayload) {
  return parseAuthenticatedUser(await apiRequest<unknown>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  }))
}

export async function register(payload: RegisterPayload) {
  return parseAuthenticatedUser(await apiRequest<unknown>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  }))
}

export function logout() {
  return apiRequest<void>('/auth/logout', { method: 'POST' })
}
