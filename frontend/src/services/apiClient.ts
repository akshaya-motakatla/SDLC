interface ApiErrorBody {
  message?: string
  errors?: Record<string, string>
}

interface CsrfResponse {
  token: string
}

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function extractErrorMessage(body: unknown, rawBody: string, status: number) {
  if (isApiErrorBody(body)) {
    if (typeof body.message === 'string') return body.message
    if (body.errors && typeof body.errors === 'object') {
      return Object.values(body.errors).filter(Boolean).join(' ')
    }
  }
  return rawBody.trim() || `The API request failed with status ${status}.`
}

function apiUrl(path: string) {
  const apiBase = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')
  return `${apiBase}${path}`
}

async function loadCsrfToken() {
  const response = await fetch(apiUrl('/auth/csrf'), { credentials: 'include' })
  if (!response.ok) {
    throw new ApiError(`Unable to prepare a secure request (HTTP ${response.status}).`, response.status)
  }
  const body: unknown = await response.json()
  if (typeof body !== 'object' || body === null || !('token' in body) || typeof body.token !== 'string') {
    throw new Error('The authentication API returned an invalid CSRF token response.')
  }
  return (body as CsrfResponse).token
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase()
  const headers = new Headers(init?.headers)
  if (init?.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    headers.set('X-XSRF-TOKEN', await loadCsrfToken())
  }

  const response = await fetch(apiUrl(path), {
    ...init,
    method,
    headers,
    credentials: 'include',
  })
  const rawBody = await response.text()
  let body: unknown

  if (rawBody) {
    try {
      body = JSON.parse(rawBody)
    } catch {
      body = undefined
    }
  }

  if (!response.ok) {
    throw new ApiError(extractErrorMessage(body, rawBody, response.status), response.status)
  }

  if (response.status === 204 || !rawBody) return undefined as T
  return body as T
}
