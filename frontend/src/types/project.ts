export interface Project {
  id: string
  name: string
  description: string
  objectives: string | null
  domain: string
  preferredStack: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface ProjectPayload {
  name: string
  description: string
  objectives: string
  domain: string
  preferredStack: string
  status: string
}
