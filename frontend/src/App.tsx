import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import {
  Activity,
  ArrowRight,
  Bell,
  Check,
  CircleHelp,
  Clock3,
  FileText,
  FlaskConical,
  FolderKanban,
  LayoutDashboard,
  ListTodo,
  LoaderCircle,
  LockKeyhole,
  LogOut,
  Mail,
  Moon,
  Plus,
  RefreshCw,
  Search,
  Settings2,
  ShieldCheck,
  Sparkles,
  Sun,
  Trash2,
  X,
} from 'lucide-react'
import { getCurrentUser, login, logout, register } from './services/authApi'
import { ApiError } from './services/apiClient'
import { createProject, deleteProject, listProjects, updateProject } from './services/projectsApi'
import ArchitectureWorkspace from './components/ArchitectureWorkspace'
import RequirementsWorkspace from './components/RequirementsWorkspace'
import type { AuthenticatedUser, LoginPayload, RegisterPayload } from './types/auth'
import type { Project, ProjectPayload } from './types/project'

type Page = 'dashboard' | 'projects' | 'requirements' | 'architecture'
type Theme = 'light' | 'dark'
type AuthMode = 'login' | 'register'

const emptyForm: ProjectPayload = {
  name: '',
  description: '',
  objectives: '',
  domain: '',
  preferredStack: '',
  status: 'DRAFT',
}

const statusLabels: Record<string, string> = {
  DRAFT: 'Draft',
  ACTIVE: 'Active',
  COMPLETED: 'Completed',
}

function getErrorMessage(error: unknown) {
  if (error instanceof TypeError) {
    return 'Could not connect to the API. Check that the backend and PostgreSQL are running, then retry.'
  }
  if (error instanceof ApiError && error.status === 401) {
    return 'Your session has expired. Sign in again to continue.'
  }
  if (error instanceof ApiError && [502, 503, 504].includes(error.status)) {
    return 'The backend is not responding. Check that Spring Boot and PostgreSQL are running, then retry.'
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? 'Recently updated'
    : new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(date)
}

function App() {
  const [page, setPage] = useState<Page>('dashboard')
  const [theme, setTheme] = useState<Theme>(() => {
    return window.localStorage.getItem('sdlc-theme') === 'dark' ? 'dark' : 'light'
  })
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedProject, setSelectedProject] = useState<Project | null>(null)
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [authLoading, setAuthLoading] = useState(true)
  const [authError, setAuthError] = useState('')
  const [hasProjectData, setHasProjectData] = useState(false)
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingProject, setEditingProject] = useState<Project | null>(null)
  const [form, setForm] = useState<ProjectPayload>(emptyForm)
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState('')
  const searchInputRef = useRef<HTMLInputElement>(null)

  const clearAuthenticatedSession = useCallback(() => {
    setUser(null)
    setProjects([])
    setSelectedProject(null)
    setHasProjectData(false)
    setLoadError('')
    setPage('dashboard')
  }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    window.localStorage.setItem('sdlc-theme', theme)
  }, [theme])

  useEffect(() => {
    void initializeSession()
  }, [])

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(''), 3500)
    return () => window.clearTimeout(timer)
  }, [toast])

  useEffect(() => {
    if (!dialogOpen) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !saving) closeDialog()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [dialogOpen, saving])

  useEffect(() => {
    const handleSearchShortcut = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        searchInputRef.current?.focus()
      }
    }
    window.addEventListener('keydown', handleSearchShortcut)
    return () => window.removeEventListener('keydown', handleSearchShortcut)
  }, [])

  async function refreshProjects() {
    setLoading(true)
    setLoadError('')
    try {
      setProjects(await listProjects())
      setHasProjectData(true)
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearAuthenticatedSession()
      } else {
        setLoadError(getErrorMessage(error))
      }
    } finally {
      setLoading(false)
    }
  }

  async function initializeSession() {
    setAuthLoading(true)
    setAuthError('')
    try {
      setUser(await getCurrentUser())
      await refreshProjects()
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setUser(null)
        setProjects([])
        setHasProjectData(false)
        setLoadError('')
      } else {
        setAuthError(getErrorMessage(error))
      }
    } finally {
      setAuthLoading(false)
    }
  }

  async function handleAuthenticated(authenticatedUser: AuthenticatedUser) {
    setUser(authenticatedUser)
    setAuthError('')
    setPage('dashboard')
    await refreshProjects()
  }

  async function handleLogout() {
    try {
      await logout()
      clearAuthenticatedSession()
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearAuthenticatedSession()
      } else {
        setToast(getErrorMessage(error))
      }
    }
  }

  const filteredProjects = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    if (!normalizedQuery) return projects
    return projects.filter((project) =>
      [project.name, project.description, project.domain, project.preferredStack]
        .join(' ')
        .toLowerCase()
        .includes(normalizedQuery),
    )
  }, [projects, query])

  const activeCount = projects.filter((project) => project.status.toUpperCase() === 'ACTIVE').length
  const completedCount = projects.filter((project) => project.status.toUpperCase() === 'COMPLETED').length
  const draftCount = projects.filter((project) => project.status.toUpperCase() === 'DRAFT').length
  const userInitials = user ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase() : 'U'

  if (authLoading) {
    return (
      <div className="auth-loading">
        <span className="brand-mark"><Sparkles size={19} /></span>
        <LoaderCircle size={18} className="spinning" />
        <span>Checking your secure session...</span>
      </div>
    )
  }

  if (!user) {
    return <AuthScreen initialError={authError} onAuthenticated={(authenticatedUser) => void handleAuthenticated(authenticatedUser)} onRetry={() => void initializeSession()} />
  }

  function openCreateDialog() {
    setEditingProject(null)
    setForm(emptyForm)
    setFormError('')
    setDialogOpen(true)
  }

  function openEditDialog(project: Project) {
    setEditingProject(project)
    setForm({
      name: project.name,
      description: project.description,
      objectives: project.objectives ?? '',
      domain: project.domain,
      preferredStack: project.preferredStack,
      status: project.status,
    })
    setFormError('')
    setDialogOpen(true)
  }

  function closeDialog() {
    setDialogOpen(false)
    setEditingProject(null)
    setFormError('')
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setFormError('')
    try {
      const savedProject = editingProject
        ? await updateProject(editingProject.id, form)
        : await createProject(form)

      setProjects((current) =>
        editingProject
          ? current.map((project) => (project.id === savedProject.id ? savedProject : project))
          : [savedProject, ...current],
      )
      if (!editingProject) setQuery('')
      setPage('projects')
      closeDialog()
      setToast(editingProject ? 'Project changes saved.' : 'Project created.')
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearAuthenticatedSession()
      } else {
        setFormError(getErrorMessage(error))
      }
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(project: Project) {
    const confirmed = window.confirm(`Delete "${project.name}"? This action cannot be undone.`)
    if (!confirmed) return

    try {
      await deleteProject(project.id)
      setProjects((current) => current.filter((item) => item.id !== project.id))
      setToast('Project deleted.')
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearAuthenticatedSession()
      } else {
        setToast(getErrorMessage(error))
      }
    }
  }

  function openRequirements(project: Project) {
    setSelectedProject(project)
    setPage('requirements')
  }

  function openArchitecture(project: Project) {
    setSelectedProject(project)
    setPage('architecture')
  }

  const pageTitle = page === 'dashboard'
    ? 'Dashboard'
    : page === 'projects'
      ? 'Projects'
      : page === 'requirements'
        ? 'Requirements'
        : 'Architecture'

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="/" onClick={(event) => { event.preventDefault(); setPage('dashboard') }} aria-label="SDLC Studio home">
          <span className="brand-mark"><Sparkles size={19} strokeWidth={2.2} /></span>
          <span className="brand-name">sdlc<span>studio</span></span>
        </a>

        <div className="workspace-switcher">
          <span className="workspace-avatar">W</span>
          <span className="workspace-copy">
            <strong>My workspace</strong>
            <small>Local workspace</small>
          </span>
        </div>

        <nav className="sidebar-nav" aria-label="Main navigation">
          <p className="nav-label">Workspace</p>
          <button className={`nav-item ${page === 'dashboard' ? 'active' : ''}`} onClick={() => setPage('dashboard')}>
            <LayoutDashboard size={17} />
            <span>Dashboard</span>
          </button>
          <button className={`nav-item ${page === 'projects' ? 'active' : ''}`} onClick={() => setPage('projects')}>
            <FolderKanban size={17} />
            <span>Projects</span>
            <span className="nav-count">{loadError && projects.length === 0 ? '—' : projects.length}</span>
          </button>

          <p className="nav-label nav-label-spaced">SDLC workflow</p>
          <button className={`nav-item ${page === 'requirements' ? 'active' : ''}`} type="button" onClick={() => selectedProject ? setPage('requirements') : setPage('projects')}>
            <FileText size={17} /><span>Requirements</span>
          </button>
          <button className={`nav-item ${page === 'architecture' ? 'active' : ''}`} type="button" onClick={() => selectedProject ? setPage('architecture') : setPage('projects')}>
            <Activity size={17} /><span>Architecture</span>
          </button>
          <div className="nav-item nav-upcoming"><ListTodo size={17} /><span>Planning</span><span className="nav-soon">Soon</span></div>
          <div className="nav-item nav-upcoming"><FlaskConical size={17} /><span>Testing</span><span className="nav-soon">Soon</span></div>

          <p className="nav-label nav-label-spaced">Insights</p>
          <div className="nav-item nav-upcoming"><ShieldCheck size={17} /><span>Traceability</span><span className="nav-soon">Soon</span></div>
          <div className="nav-item nav-upcoming"><Settings2 size={17} /><span>Settings</span><span className="nav-soon">Soon</span></div>
        </nav>

        <div className="sidebar-bottom">
          <div className="phase-card">
            <span className="phase-icon"><Sparkles size={15} /></span>
            <div>
              <strong>Build in progress</strong>
              <p>Project workspace foundations</p>
            </div>
            <span className="phase-dot" aria-label="In progress" />
          </div>
          <button className="help-link" type="button" onClick={() => setToast('Help and documentation will be available in a later phase.')}>
            <CircleHelp size={16} /><span>Help & documentation</span>
          </button>
          <div className="profile">
            <span className="profile-avatar">{userInitials}</span>
            <span className="profile-copy"><strong>{user.firstName} {user.lastName}</strong><small>{user.email}</small></span>
            <button className="logout-button" type="button" onClick={() => void handleLogout()} aria-label="Sign out" title="Sign out"><LogOut size={15} /></button>
          </div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumbs"><span>Workspace</span><span className="breadcrumb-slash">/</span><strong>{pageTitle}</strong></div>
          <div className="topbar-actions">
            <label className="search-box">
              <Search size={16} />
              <input
                type="search"
                ref={searchInputRef}
                placeholder="Search projects..."
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                aria-label="Search projects"
              />
              <kbd>⌘ K</kbd>
            </label>
            <button className="icon-button theme-toggle" type="button" onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')} aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`} title={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}>
              {theme === 'light' ? <Moon size={17} /> : <Sun size={17} />}
            </button>
            <button className="icon-button notification-button" type="button" onClick={() => setToast('Notifications are not connected in this phase.')} aria-label="Notifications">
              <Bell size={17} />
            </button>
            <button className="icon-button topbar-logout" type="button" onClick={() => void handleLogout()} aria-label="Sign out" title="Sign out">
              <LogOut size={16} />
            </button>
            <span className="topbar-avatar" aria-label={`${user.firstName} ${user.lastName}`}>{userInitials}</span>
          </div>
        </header>

        <div className="page-content">
          {page !== 'requirements' && page !== 'architecture' && (
            <div className="page-heading">
              <div>
                <div className="eyebrow"><span className="eyebrow-line" /> YOUR ENGINEERING WORKSPACE</div>
                <h1>{page === 'dashboard' ? 'Good to see you.' : 'Your projects'}</h1>
                <p>{page === 'dashboard' ? 'A clear view of your software delivery, from idea to release.' : 'Create and manage your software project workspaces.'}</p>
              </div>
              <div className="heading-actions">
                <button className="button button-secondary refresh-button" type="button" onClick={() => void refreshProjects()} disabled={loading}>
                  <RefreshCw size={15} className={loading ? 'spinning' : ''} /> Refresh
                </button>
                <button className="button button-primary" type="button" onClick={openCreateDialog}>
                  <Plus size={17} /> New project
                </button>
              </div>
            </div>
          )}

          {loadError && (
            <div className="connection-banner" role="alert">
              <span className="connection-icon"><Activity size={17} /></span>
              <div><strong>Project data is unavailable</strong><p>{loadError}</p></div>
              <button className="button button-secondary banner-retry" type="button" onClick={() => void refreshProjects()} disabled={loading}>Retry</button>
            </div>
          )}

          {page === 'requirements' && selectedProject ? (
            <RequirementsWorkspace
              project={selectedProject}
              onBack={() => setPage('projects')}
              onSessionExpired={clearAuthenticatedSession}
            />
          ) : page === 'architecture' && selectedProject ? (
            <ArchitectureWorkspace
              project={selectedProject}
              theme={theme}
              onBack={() => setPage('projects')}
              onOpenRequirements={() => setPage('requirements')}
              onSessionExpired={clearAuthenticatedSession}
            />
          ) : page === 'dashboard' ? (
            <Dashboard
              projects={filteredProjects}
              allProjectsCount={projects.length}
              activeCount={activeCount}
              completedCount={completedCount}
              draftCount={draftCount}
              loading={loading}
              hasProjectData={hasProjectData}
              hasLoadError={Boolean(loadError)}
              searchQuery={query}
              onCreate={openCreateDialog}
              onOpenProjects={() => setPage('projects')}
              onEdit={openEditDialog}
              onOpenRequirements={openRequirements}
              onOpenArchitecture={openArchitecture}
            />
          ) : (
            <ProjectsPage
              projects={filteredProjects}
              loading={loading}
              hasProjectData={hasProjectData}
              hasLoadError={Boolean(loadError)}
              searchQuery={query}
              onCreate={openCreateDialog}
              onEdit={openEditDialog}
              onDelete={(project) => void handleDelete(project)}
              onOpenRequirements={openRequirements}
              onOpenArchitecture={openArchitecture}
            />
          )}
        </div>
      </main>

      <footer className="app-footer">
        <span><span className={`footer-status-dot ${loadError ? 'offline' : ''}`} /> {loading ? 'Syncing workspace...' : loadError ? 'API disconnected' : 'Workspace synced'}</span>
        <span>Built for thoughtful software delivery <span className="footer-spark">✳</span></span>
      </footer>

      {dialogOpen && (
        <div className="dialog-backdrop" onMouseDown={(event) => event.target === event.currentTarget && !saving && closeDialog()}>
          <section className="project-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title">
            <div className="dialog-header">
              <div>
                <div className="dialog-icon"><FolderKanban size={18} /></div>
                <h2 id="dialog-title">{editingProject ? 'Edit project' : 'Create a project'}</h2>
                <p>{editingProject ? 'Update your project workspace details.' : 'Start with the context your team needs to build well.'}</p>
              </div>
              <button className="icon-button close-dialog" type="button" onClick={closeDialog} disabled={saving} aria-label="Close dialog"><X size={18} /></button>
            </div>
            <form className="project-form" onSubmit={(event) => void handleSave(event)}>
              <label className="form-field">
                <span>Project name <em>*</em></span>
                <input autoFocus required maxLength={255} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="e.g. Customer onboarding platform" />
              </label>
              <label className="form-field">
                <span>Description <em>*</em></span>
                <textarea required rows={3} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} placeholder="What are you building and who is it for?" />
              </label>
              <label className="form-field">
                <span>Objectives <small>Optional</small></span>
                <textarea rows={2} value={form.objectives} onChange={(event) => setForm({ ...form, objectives: event.target.value })} placeholder="What outcomes should this project achieve?" />
              </label>
              <div className="form-row">
                <label className="form-field">
                  <span>Domain <em>*</em></span>
                  <input required maxLength={100} value={form.domain} onChange={(event) => setForm({ ...form, domain: event.target.value })} placeholder="e.g. Fintech" />
                </label>
                <label className="form-field">
                  <span>Technology stack <em>*</em></span>
                  <input required maxLength={255} value={form.preferredStack} onChange={(event) => setForm({ ...form, preferredStack: event.target.value })} placeholder="e.g. Java, React" />
                </label>
              </div>
              <div className="form-row form-row-bottom">
                <label className="form-field">
                  <span>Status</span>
                  <select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })}>
                    {!Object.keys(statusLabels).includes(form.status.toUpperCase()) && <option value={form.status}>{form.status}</option>}
                    {Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                  </select>
                </label>
                <span className="required-note"><em>*</em> Required fields</span>
              </div>
              {formError && <p className="form-error" role="alert">{formError}</p>}
              <div className="dialog-actions">
                <button className="button button-secondary" type="button" onClick={closeDialog} disabled={saving}>Cancel</button>
                <button className="button button-primary" type="submit" disabled={saving}>
                  {saving ? <LoaderCircle size={16} className="spinning" /> : <Check size={16} />}
                  {saving ? 'Saving...' : editingProject ? 'Save changes' : 'Create project'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}

      {toast && <div className="toast" role="status"><Check size={16} />{toast}</div>}
    </div>
  )
}

function AuthScreen({
  initialError,
  onAuthenticated,
  onRetry,
}: {
  initialError: string
  onAuthenticated: (user: AuthenticatedUser) => void
  onRetry: () => void
}) {
  const [mode, setMode] = useState<AuthMode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [username, setUsername] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const authenticatedUser = mode === 'login'
        ? await login({ email, password } satisfies LoginPayload)
        : await register({
            username,
            email,
            password,
            firstName,
            lastName,
          } satisfies RegisterPayload)
      onAuthenticated(authenticatedUser)
    } catch (requestError) {
      setError(requestError instanceof ApiError && requestError.status === 401
        ? 'Invalid email or password.'
        : getErrorMessage(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  function changeMode(nextMode: AuthMode) {
    setMode(nextMode)
    setError('')
  }

  return (
    <main className="auth-page">
      <section className="auth-story">
        <a className="brand auth-brand" href="/" onClick={(event) => event.preventDefault()} aria-label="SDLC Studio">
          <span className="brand-mark"><Sparkles size={19} /></span>
          <span className="brand-name">sdlc<span>studio</span></span>
        </a>
        <div className="auth-story-copy">
          <span className="auth-kicker"><span /> SOFTWARE DELIVERY, WITH CLARITY</span>
          <h1>Make good ideas<br />ready to build.</h1>
          <p>Bring your requirements, architecture, plans, and testing into one thoughtful engineering workspace.</p>
        </div>
        <div className="auth-story-footer">
          <span><ShieldCheck size={15} /> Human-reviewed by design</span>
          <span>Secure workspace access</span>
        </div>
        <div className="auth-decoration"><span /><span /><span /><span /></div>
      </section>

      <section className="auth-form-side">
        <div className="auth-form-wrap">
          <div className="auth-mobile-brand">
            <span className="brand-mark"><Sparkles size={18} /></span>
            <span className="brand-name">sdlc<span>studio</span></span>
          </div>
          <div className="auth-form-heading">
            <span className="auth-small-label">{mode === 'login' ? 'WELCOME BACK' : 'GET STARTED'}</span>
            <h2>{mode === 'login' ? 'Sign in to your workspace' : 'Create your account'}</h2>
            <p>{mode === 'login' ? 'Use your account to access your project workspaces.' : 'Your projects are private to your account.'}</p>
          </div>

          <div className="auth-mode-tabs" role="tablist" aria-label="Account access">
            <button type="button" className={mode === 'login' ? 'selected' : ''} onClick={() => changeMode('login')} role="tab" aria-selected={mode === 'login'}>Sign in</button>
            <button type="button" className={mode === 'register' ? 'selected' : ''} onClick={() => changeMode('register')} role="tab" aria-selected={mode === 'register'}>Create account</button>
          </div>

          {initialError && (
            <div className="auth-error" role="alert">
              <strong>Could not check your session</strong>
              <span>{initialError}</span>
              <button type="button" onClick={onRetry}>Retry connection</button>
            </div>
          )}

          <form className="auth-form" onSubmit={(event) => void handleSubmit(event)}>
            {mode === 'register' && (
              <>
                <label className="form-field">
                  <span>Username</span>
                  <input required minLength={3} maxLength={50} pattern="[A-Za-z0-9._-]+" autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Choose a username" />
                </label>
                <div className="form-row">
                  <label className="form-field">
                    <span>First name</span>
                    <input required maxLength={100} autoComplete="given-name" value={firstName} onChange={(event) => setFirstName(event.target.value)} placeholder="First name" />
                  </label>
                  <label className="form-field">
                    <span>Last name</span>
                    <input required maxLength={100} autoComplete="family-name" value={lastName} onChange={(event) => setLastName(event.target.value)} placeholder="Last name" />
                  </label>
                </div>
              </>
            )}
            <label className="form-field">
              <span>Email address</span>
              <span className="input-with-icon"><Mail size={15} /><input required type="email" maxLength={255} autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@company.com" /></span>
            </label>
            <label className="form-field">
              <span>Password {mode === 'register' && <small>At least 12 characters</small>}</span>
              <span className="input-with-icon"><LockKeyhole size={15} /><input required type="password" minLength={mode === 'register' ? 12 : undefined} maxLength={72} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder={mode === 'login' ? 'Enter your password' : 'Create a strong password'} /></span>
            </label>
            {error && <p className="form-error" role="alert">{error}</p>}
            <button className="button button-primary auth-submit" type="submit" disabled={submitting}>
              {submitting ? <LoaderCircle size={16} className="spinning" /> : <LockKeyhole size={15} />}
              {submitting ? 'Please wait...' : mode === 'login' ? 'Sign in securely' : 'Create account'}
              {!submitting && <ArrowRight size={15} />}
            </button>
          </form>

          <p className="auth-security-note"><ShieldCheck size={14} /> Passwords are securely hashed. Sessions use HttpOnly cookies.</p>
          <p className="auth-switch-prompt">
            {mode === 'login' ? 'New to SDLC Studio?' : 'Already have an account?'}
            <button type="button" onClick={() => changeMode(mode === 'login' ? 'register' : 'login')}>
              {mode === 'login' ? 'Create an account' : 'Sign in'}
            </button>
          </p>
        </div>
        <div className="auth-legal"><span>Secure by design</span><span>Private project workspaces</span></div>
      </section>
    </main>
  )
}

interface DashboardProps {
  projects: Project[]
  allProjectsCount: number
  activeCount: number
  completedCount: number
  draftCount: number
  loading: boolean
  hasProjectData: boolean
  hasLoadError: boolean
  searchQuery: string
  onCreate: () => void
  onOpenProjects: () => void
  onEdit: (project: Project) => void
  onOpenRequirements: (project: Project) => void
  onOpenArchitecture: (project: Project) => void
}

function Dashboard({
  projects,
  allProjectsCount,
  activeCount,
  completedCount,
  draftCount,
  loading,
  hasProjectData,
  hasLoadError,
  searchQuery,
  onCreate,
  onOpenProjects,
  onEdit,
  onOpenRequirements,
  onOpenArchitecture,
}: DashboardProps) {
  const metricValue = (value: number) => hasLoadError && !hasProjectData ? '—' : value

  return (
    <>
      <section className="metrics-grid" aria-label="Project metrics">
        <MetricCard icon={<FolderKanban size={17} />} label="Total projects" value={metricValue(allProjectsCount)} caption="Across this workspace" color="violet" />
        <MetricCard icon={<Activity size={17} />} label="Active projects" value={metricValue(activeCount)} caption="Currently in progress" color="blue" />
        <MetricCard icon={<Check size={17} />} label="Completed" value={metricValue(completedCount)} caption="Marked as complete" color="green" />
        <MetricCard icon={<FileText size={17} />} label="Requirements" value="—" caption="Available in a later phase" color="amber" unavailable />
        <MetricCard icon={<ListTodo size={17} />} label="Development tasks" value="—" caption="Available in a later phase" color="cyan" unavailable />
        <MetricCard icon={<FlaskConical size={17} />} label="Test coverage" value="—" caption="Available in a later phase" color="rose" unavailable />
      </section>

      <section className="dashboard-grid">
        <div className="panel projects-panel">
          <div className="panel-heading">
            <div><h2>Recent projects</h2><p>Your latest workspaces at a glance.</p></div>
            <button className="text-button" type="button" onClick={onOpenProjects}>View all <ArrowRight size={15} /></button>
          </div>
          {loading && projects.length === 0 ? (
            <div className="loading-state"><LoaderCircle size={19} className="spinning" /><span>Loading projects from your workspace...</span></div>
          ) : projects.length > 0 ? (
            <div className="project-list">
              {projects.slice(0, 5).map((project) => (
                <ProjectListItem
                  key={project.id}
                  project={project}
                  onEdit={onEdit}
                  onOpenRequirements={onOpenRequirements}
                  onOpenArchitecture={onOpenArchitecture}
                />
              ))}
            </div>
          ) : hasLoadError && !hasProjectData ? (
            <UnavailableProjects />
          ) : (
            <EmptyProjects searchQuery={searchQuery} onCreate={onCreate} />
          )}
        </div>

        <div className="panel workflow-panel">
          <div className="panel-heading">
            <div><h2>SDLC workflow</h2><p>From project idea to tested delivery.</p></div>
            <span className="phase-badge">ROADMAP</span>
          </div>
          <div className="workflow-steps">
            <WorkflowStep number="01" title="Project workspace" subtitle={hasLoadError && !hasProjectData ? 'Project data is not available' : `${allProjectsCount} ${allProjectsCount === 1 ? 'project' : 'projects'} in this workspace`} state={hasLoadError && !hasProjectData ? 'upcoming' : 'complete'} />
            <WorkflowStep number="02" title="Requirements & review" subtitle="Structured analysis with human approval" state="upcoming" />
            <WorkflowStep number="03" title="Architecture & planning" subtitle="Design decisions and delivery roadmap" state="upcoming" />
            <WorkflowStep number="04" title="QA & traceability" subtitle="Test evidence linked to requirements" state="upcoming" last />
          </div>
          <div className="workflow-note"><ShieldCheck size={16} /><span>AI-generated artifacts will require human review before they move forward.</span></div>
          <div className="draft-summary">
            <span className="draft-indicator" />
            <span>{hasLoadError && !hasProjectData ? 'Draft count unavailable while the API is disconnected.' : <><strong>{draftCount}</strong> {draftCount === 1 ? 'project is' : 'projects are'} currently in draft status.</>}</span>
            <button type="button" onClick={onOpenProjects} aria-label="View projects"><ArrowRight size={15} /></button>
          </div>
        </div>
      </section>

      <section className="lower-grid">
        <div className="panel activity-panel">
          <div className="panel-heading">
            <div><h2>Agent activity</h2><p>Recent AI-assisted work in this workspace.</p></div>
            <span className="coming-label">COMING SOON</span>
          </div>
          <div className="activity-empty">
            <span className="activity-empty-icon"><Sparkles size={19} /></span>
            <div><strong>No agent runs yet</strong><p>Agent activity will appear here when the AI workflow is implemented and connected.</p></div>
          </div>
        </div>
        <div className="panel quality-panel">
          <div className="quality-decoration"><span /><span /><span /></div>
          <div className="quality-content">
            <span className="quality-icon"><ShieldCheck size={18} /></span>
            <div className="quality-copy">
              <span className="quality-kicker">BUILT FOR TRUST</span>
              <h2>Human review stays in the loop.</h2>
              <p>Generated engineering artifacts will be clearly labeled and require your approval before downstream work begins.</p>
            </div>
          </div>
          <div className="quality-footer"><span>Review-first workflow</span><span><Check size={13} /> Always</span></div>
        </div>
      </section>
    </>
  )
}

interface MetricCardProps {
  icon: ReactNode
  label: string
  value: number | string
  caption: string
  color: string
  unavailable?: boolean
}

function MetricCard({ icon, label, value, caption, color, unavailable = false }: MetricCardProps) {
  return (
    <article className={`metric-card metric-${color}`}>
      <div className="metric-top">
        <span className="metric-icon">{icon}</span>
        {unavailable && <span className="metric-unavailable">NOT TRACKED</span>}
      </div>
      <p className="metric-label">{label}</p>
      <div className="metric-value">{value}</div>
      <p className="metric-caption">{caption}</p>
    </article>
  )
}

function WorkflowStep({ number, title, subtitle, state, last = false }: { number: string; title: string; subtitle: string; state: 'complete' | 'upcoming'; last?: boolean }) {
  return (
    <div className={`workflow-step ${last ? 'last' : ''}`}>
      <div className={`step-number ${state}`}>{state === 'complete' ? <Check size={14} /> : number}</div>
      <div className="step-copy"><strong>{title}</strong><p>{subtitle}</p></div>
      {state === 'complete' ? <span className="step-state done">Ready</span> : <span className="step-state">Planned</span>}
    </div>
  )
}

function ProjectListItem({
  project,
  onEdit,
  onOpenRequirements,
  onOpenArchitecture,
}: {
  project: Project
  onEdit: (project: Project) => void
  onOpenRequirements: (project: Project) => void
  onOpenArchitecture: (project: Project) => void
}) {
  return (
    <article className="project-list-item">
      <div className="project-letter">{project.name.trim().charAt(0).toUpperCase() || 'P'}</div>
      <div className="project-list-copy">
        <strong>{project.name}</strong>
        <span>{project.domain} <span className="project-dot-separator">·</span> Updated {formatDate(project.updatedAt)}</span>
      </div>
      <StatusBadge status={project.status} />
      <button className="row-action requirements-open-action" type="button" onClick={() => onOpenRequirements(project)} aria-label={`Open requirements for ${project.name}`} title="Open requirements"><FileText size={15} /></button>
      <button className="row-action requirements-open-action" type="button" onClick={() => onOpenArchitecture(project)} aria-label={`Open architecture for ${project.name}`} title="Open architecture"><Activity size={15} /></button>
      <button className="row-action" type="button" onClick={() => onEdit(project)} aria-label={`Edit ${project.name}`}><ArrowRight size={16} /></button>
    </article>
  )
}

function ProjectsPage({
  projects,
  loading,
  hasProjectData,
  hasLoadError,
  searchQuery,
  onCreate,
  onEdit,
  onDelete,
  onOpenRequirements,
  onOpenArchitecture,
}: {
  projects: Project[]
  loading: boolean
  hasProjectData: boolean
  hasLoadError: boolean
  searchQuery: string
  onCreate: () => void
  onEdit: (project: Project) => void
  onDelete: (project: Project) => void
  onOpenRequirements: (project: Project) => void
  onOpenArchitecture: (project: Project) => void
}) {
  return (
    <section className="panel projects-page-panel">
      <div className="panel-heading project-page-heading">
        <div>
          <h2>All projects <span className="heading-count">{hasLoadError && projects.length === 0 ? '—' : projects.length}</span></h2>
          <p>Manage project context, technology choices, and delivery status.</p>
        </div>
        <div className="table-tools">
          <span className="table-updated"><Clock3 size={14} /> {hasLoadError ? 'API connection unavailable' : 'Live from workspace API'}</span>
          <button className="button button-primary button-small" type="button" onClick={onCreate}><Plus size={15} /> New project</button>
        </div>
      </div>
      <div className="project-table-wrap">
        <table className="project-table">
          <thead>
            <tr><th>PROJECT</th><th>DOMAIN</th><th>TECHNOLOGY</th><th>STATUS</th><th>LAST UPDATED</th><th><span className="sr-only">Actions</span></th></tr>
          </thead>
          <tbody>
            {loading && projects.length === 0 ? (
              <tr><td colSpan={6}><div className="loading-state"><LoaderCircle size={19} className="spinning" /><span>Loading projects from your workspace...</span></div></td></tr>
            ) : projects.length > 0 ? projects.map((project) => (
              <tr key={project.id}>
                <td>
                  <div className="table-project">
                    <span className="project-letter small-letter">{project.name.trim().charAt(0).toUpperCase() || 'P'}</span>
                    <span><strong>{project.name}</strong><small>{project.description}</small></span>
                  </div>
                </td>
                <td><span className="table-domain">{project.domain}</span></td>
                <td><span className="stack-label">{project.preferredStack}</span></td>
                <td><StatusBadge status={project.status} /></td>
                <td><span className="date-label">{formatDate(project.updatedAt)}</span></td>
                <td>
                  <div className="table-actions">
                    <button className="row-action" type="button" onClick={() => onOpenRequirements(project)} aria-label={`Open requirements for ${project.name}`} title="Open requirements"><FileText size={15} /></button>
                    <button className="row-action" type="button" onClick={() => onOpenArchitecture(project)} aria-label={`Open architecture for ${project.name}`} title="Open architecture"><Activity size={15} /></button>
                    <button className="row-action" type="button" onClick={() => onEdit(project)} aria-label={`Edit ${project.name}`} title="Edit project"><span className="edit-word">Edit</span></button>
                    <button className="row-action delete-action" type="button" onClick={() => onDelete(project)} aria-label={`Delete ${project.name}`} title="Delete project"><Trash2 size={15} /></button>
                  </div>
                </td>
              </tr>
            )) : hasLoadError && !hasProjectData ? (
              <tr><td colSpan={6}><UnavailableProjects /></td></tr>
            ) : (
              <tr><td colSpan={6}><EmptyProjects searchQuery={searchQuery} onCreate={onCreate} /></td></tr>
            )}
          </tbody>
        </table>
      </div>
      <div className="table-footer"><span>{loading ? 'Refreshing project data...' : hasLoadError && projects.length === 0 ? 'Waiting for API connection' : `${projects.length} ${projects.length === 1 ? 'project' : 'projects'} shown`}</span><span className={`data-source ${hasLoadError ? 'unavailable' : ''}`}>{!hasLoadError && <span />} {hasLoadError ? 'Data source unavailable' : 'Persisted API data'}</span></div>
    </section>
  )
}

function StatusBadge({ status }: { status: string }) {
  const normalized = status.toUpperCase()
  const label = statusLabels[normalized] ?? status
  return <span className={`status-badge status-${normalized.toLowerCase()}`}><span />{label}</span>
}

function EmptyProjects({ searchQuery, onCreate }: { searchQuery: string; onCreate: () => void }) {
  if (searchQuery.trim()) {
    return <div className="empty-state"><span className="empty-icon"><Search size={18} /></span><strong>No matching projects</strong><p>Try a different search term.</p></div>
  }
  return (
    <div className="empty-state">
      <span className="empty-icon"><FolderKanban size={19} /></span>
      <strong>No projects yet</strong>
      <p>Create a workspace to keep your project context and delivery artifacts together.</p>
      <button className="button button-primary button-small" type="button" onClick={onCreate}><Plus size={15} /> Create your first project</button>
    </div>
  )
}

function UnavailableProjects() {
  return (
    <div className="empty-state">
      <span className="empty-icon"><Activity size={18} /></span>
      <strong>Project data could not be loaded</strong>
      <p>Reconnect the backend and use Retry to load persisted workspace data. No sample projects are shown.</p>
    </div>
  )
}

export default App
