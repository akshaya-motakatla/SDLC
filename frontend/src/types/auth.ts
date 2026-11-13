export interface AuthenticatedUser {
  id: string
  username: string
  email: string
  firstName: string
  lastName: string
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
  firstName: string
  lastName: string
}

export interface LoginPayload {
  email: string
  password: string
}
