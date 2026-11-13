import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import {
  ArrowLeft,
  Check,
  ChevronDown,
  ChevronUp,
  Clock3,
  ListTodo,
  LoaderCircle,
  Pencil,
  RefreshCw,
  Sparkles,
  Trash2,
  X,
} from 'lucide-react'
import { ApiError } from '../services/apiClient'
import {
  approveDevelopmentTask,
  deleteDevelopmentTask,
  generateDevelopmentTasks,
  listDevelopmentTasks,
  rejectDevelopmentTask,
  updateDevelopmentTask,
  updateDevelopmentTaskStatus,
} from '../services/developmentPlanningApi'
import { listArchitectureDocuments } from '../services/architectureApi'
import { listRequirements } from '../services/requirementsApi'
import type { ArchitectureDocument } from '../types/architecture'
import type {
  DevelopmentTask,
  DevelopmentTaskUpdatePayload,
  TaskCategory,
  TaskPriority,
  TaskStatus,
} from '../types/developmentTask'
import type { Project } from '../types/project'
import type { Requirement } from '../types/requirement'

interface DevelopmentPlanningWorkspaceProps {
  project: Project
  onBack: () => void
  onSessionExpired: () => void
}

const categories: TaskCategory[] = ['FRONTEND', 'BACKEND', 'DATABASE', 'API', 'SECURITY', 'INTEGRATION', 'OTHER']
const priorities: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const statuses: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED']

const emptyEdit: DevelopmentTaskUpdatePayload = {
  title: '',
  description: '',
  category: 'BACKEND',
  priority: 'MEDIUM',
  estimatedEffortHours: 4,
  milestone: '',
  acceptanceCriteria: [],
  relatedRequirementIds: [],
  architectureReferenceIds: [],
  dependencyIds: [],
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    return 'Your session has expired. Sign in again to continue.'
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function titleCase(value: string) {
  return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export default function DevelopmentPlanningWorkspace({
  project,
  onBack,
  onSessionExpired,
}: DevelopmentPlanningWorkspaceProps) {
  const [tasks, setTasks] = useState<DevelopmentTask[]>([])
  const [requirements, setRequirements] = useState<Requirement[]>([])
  const [architectures, setArchitectures] = useState<ArchitectureDocument[]>([])
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [providerLabel, setProviderLabel] = useState('')
  const [query, setQuery] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [milestoneFilter, setMilestoneFilter] = useState('')
  const [editingTask, setEditingTask] = useState<DevelopmentTask | null>(null)
  const [editForm, setEditForm] = useState<DevelopmentTaskUpdatePayload>(emptyEdit)
  const [saving, setSaving] = useState(false)
  const [expandedTaskId, setExpandedTaskId] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    void Promise.all([
      listDevelopmentTasks(project.id),
      listRequirements(project.id),
      listArchitectureDocuments(project.id),
    ])
      .then(([loadedTasks, loadedRequirements, loadedArchitectures]) => {
        if (!active) return
        setTasks(loadedTasks)
        setRequirements(loadedRequirements)
        setArchitectures(loadedArchitectures)
      })
      .catch((requestError: unknown) => {
        if (!active) return
        if (requestError instanceof ApiError && requestError.status === 401) {
          onSessionExpired()
        } else {
          setError(errorMessage(requestError))
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [project.id, onSessionExpired])

  const approvedRequirements = useMemo(
    () => requirements.filter((requirement) => requirement.status === 'APPROVED'),
    [requirements],
  )
  const approvedArchitectures = useMemo(
    () => architectures.filter((document) => document.status === 'APPROVED'),
    [architectures],
  )
  const selectableRequirements = [
    ...approvedRequirements.map((requirement) => ({
      id: requirement.id,
      label: `${requirement.code} · ${requirement.title}`,
    })),
    ...(editingTask?.relatedRequirements
      .filter((reference) => !approvedRequirements.some((requirement) => requirement.id === reference.id))
      .map((reference) => ({ id: reference.id, label: `${reference.code} · ${reference.title} (existing source)` })) ?? []),
  ]
  const selectableArchitectures = [
    ...approvedArchitectures.map((architecture) => ({
      id: architecture.id,
      label: `${architecture.title} · v${architecture.version}`,
    })),
    ...(editingTask?.architectureReferences
      .filter((reference) => !approvedArchitectures.some((architecture) => architecture.id === reference.id))
      .map((reference) => ({ id: reference.id, label: `${reference.title} · v${reference.version ?? '—'} (existing source)` })) ?? []),
  ]

  const filteredTasks = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase()
    return tasks.filter((task) => {
      const taskText = [
        task.taskId,
        task.title,
        task.description,
        task.milestone ?? '',
        ...task.relatedRequirements.map((requirement) => `${requirement.code} ${requirement.title}`),
        ...task.architectureReferences.map((architecture) => architecture.title),
      ].join(' ').toLowerCase()
      return (!normalizedQuery || taskText.includes(normalizedQuery))
        && (!categoryFilter || task.category === categoryFilter)
        && (!priorityFilter || task.priority === priorityFilter)
        && (!statusFilter || task.status === statusFilter)
        && (!milestoneFilter || (task.milestone ?? '').toLowerCase().includes(milestoneFilter.toLowerCase()))
    })
  }, [tasks, query, categoryFilter, priorityFilter, statusFilter, milestoneFilter])

  const groupedTasks = useMemo(() => {
    const groups = new Map<string, DevelopmentTask[]>()
    for (const task of filteredTasks) {
      const milestone = task.milestone?.trim() || 'Unscheduled'
      groups.set(milestone, [...(groups.get(milestone) ?? []), task])
    }
    return [...groups.entries()]
  }, [filteredTasks])

  async function refreshTasks() {
    setLoading(true)
    setError('')
    try {
      setTasks(await listDevelopmentTasks(project.id))
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) onSessionExpired()
      else setError(errorMessage(requestError))
    } finally {
      setLoading(false)
    }
  }

  async function handleGenerate() {
    setGenerating(true)
    setError('')
    setActionError('')
    try {
      const result = await generateDevelopmentTasks(project.id)
      setTasks((current) => [...result.tasks, ...current])
      setProviderLabel(result.demo ? `${result.provider} · Demo output, not AI-generated` : result.provider)
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) onSessionExpired()
      else setError(errorMessage(requestError))
    } finally {
      setGenerating(false)
    }
  }

  function beginEdit(task: DevelopmentTask) {
    setEditingTask(task)
    setActionError('')
    setEditForm({
      title: task.title,
      description: task.description,
      category: task.category,
      priority: task.priority,
      estimatedEffortHours: task.estimatedEffortHours ?? 1,
      milestone: task.milestone ?? '',
      acceptanceCriteria: task.acceptanceCriteria,
      relatedRequirementIds: task.relatedRequirements.map((requirement) => requirement.id),
      architectureReferenceIds: task.architectureReferences.map((architecture) => architecture.id),
      dependencyIds: task.dependencyIds,
    })
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editingTask) return
    setSaving(true)
    setActionError('')
    try {
      const updated = await updateDevelopmentTask(project.id, editingTask.id, editForm)
      setTasks((current) => current.map((task) => task.id === updated.id ? updated : task))
      setEditingTask(null)
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) onSessionExpired()
      else setActionError(errorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  async function handleReview(task: DevelopmentTask, action: 'approve' | 'reject') {
    setActionError('')
    try {
      const updated = action === 'approve'
        ? await approveDevelopmentTask(project.id, task.id)
        : await rejectDevelopmentTask(project.id, task.id)
      setTasks((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) onSessionExpired()
      else setActionError(errorMessage(requestError))
    }
  }

  async function handleStatusChange(task: DevelopmentTask, status: TaskStatus) {
    setActionError('')
    try {
      const updated = await updateDevelopmentTaskStatus(project.id, task.id, status)
      setTasks((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) onSessionExpired()
      else setActionError(errorMessage(requestError))
    }
  }

  async function handleDelete(task: DevelopmentTask) {
    if (!window.confirm(`Delete ${task.taskId}: "${task.title}"?`)) return
    setActionError('')
    try {
      await deleteDevelopmentTask(project.id, task.id)
      setTasks((current) => current.filter((item) => item.id !== task.id))
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) onSessionExpired()
      else setActionError(errorMessage(requestError))
    }
  }

  function setCriteria(value: string) {
    setEditForm((current) => ({
      ...current,
      acceptanceCriteria: value.split('\n').map((criterion) => criterion.trim()).filter(Boolean),
    }))
  }

  function toggleSelection(field: 'relatedRequirementIds' | 'architectureReferenceIds' | 'dependencyIds', id: string) {
    setEditForm((current) => ({
      ...current,
      [field]: current[field].includes(id)
        ? current[field].filter((value) => value !== id)
        : [...current[field], id],
    }))
  }

  return (
    <section className="planning-workspace" aria-labelledby="planning-title">
      <button className="text-button requirements-back" type="button" onClick={onBack}>
        <ArrowLeft size={15} /> Back to projects
      </button>
      <header className="requirements-heading">
        <div>
          <span className="eyebrow"><span className="eyebrow-line" /> DEVELOPMENT PLANNING</span>
          <h2 id="planning-title">{project.name}</h2>
          <p>Turn approved requirements and architecture into reviewable, traceable delivery tasks.</p>
        </div>
        <div className="planning-heading-actions">
          <button className="button button-secondary" type="button" onClick={() => void refreshTasks()} disabled={loading}>
            <RefreshCw size={15} className={loading ? 'spinning' : ''} /> Refresh
          </button>
          <button className="button button-primary" type="button" onClick={() => void handleGenerate()} disabled={generating || loading}>
            {generating ? <LoaderCircle size={16} className="spinning" /> : <Sparkles size={16} />}
            {generating ? 'Generating...' : 'Generate Tasks'}
          </button>
        </div>
      </header>

      <section className="project-context panel" aria-label="Project context">
        <div><strong>Generation sources</strong><span>{approvedRequirements.length} approved requirements · {approvedArchitectures.length} approved architecture documents</span></div>
        <p>{project.description}</p>
        {project.objectives && <p><strong>Objectives:</strong> {project.objectives}</p>}
        {!approvedRequirements.length && <p className="planning-source-warning">Approve at least one requirement before generating tasks.</p>}
      </section>

      {providerLabel && <p className={`provider-notice ${providerLabel.includes('Demo output') ? 'provider-demo' : ''}`} role="status">Generation source: <strong>{providerLabel}</strong></p>}
      {tasks.some((task) => task.demo) && <p className="provider-notice provider-demo" role="note">Tasks marked MOCK_DEMO are sample output from the local mock provider.</p>}
      {error && <div className="requirements-error" role="alert">{error}</div>}
      {actionError && <div className="requirements-error" role="alert">{actionError}</div>}

      <div className="planning-filters panel" aria-label="Task filters">
        <label className="form-field"><span>Search tasks</span><input aria-label="Search tasks" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Title, task ID, requirement..." /></label>
        <label className="form-field"><span>Category</span><select aria-label="Filter by category" value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="">All categories</option>{categories.map((category) => <option key={category} value={category}>{titleCase(category)}</option>)}</select></label>
        <label className="form-field"><span>Priority</span><select aria-label="Filter by priority" value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)}><option value="">All priorities</option>{priorities.map((priority) => <option key={priority} value={priority}>{titleCase(priority)}</option>)}</select></label>
        <label className="form-field"><span>Status</span><select aria-label="Filter by status" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="">All statuses</option>{statuses.map((status) => <option key={status} value={status}>{titleCase(status)}</option>)}</select></label>
        <label className="form-field"><span>Milestone</span><input aria-label="Filter by milestone" value={milestoneFilter} onChange={(event) => setMilestoneFilter(event.target.value)} placeholder="Any milestone" /></label>
      </div>

      {loading ? (
        <div className="requirements-loading" role="status"><LoaderCircle size={19} className="spinning" /> Loading development tasks...</div>
      ) : tasks.length === 0 ? (
        <div className="requirements-empty panel">
          <span className="empty-icon"><ListTodo size={19} /></span>
          <strong>No development tasks yet</strong>
          <p>Generate a draft plan from approved requirements and architecture. Every generated task still needs your review.</p>
          <button className="button button-primary button-small" type="button" onClick={() => void handleGenerate()} disabled={generating || approvedRequirements.length === 0}>
            <Sparkles size={15} /> Generate Tasks
          </button>
        </div>
      ) : filteredTasks.length === 0 ? (
        <div className="planning-no-results panel">No tasks match the current search and filters.</div>
      ) : (
        <div className="planning-groups">
          {groupedTasks.map(([milestone, groupTasks]) => (
            <section className="planning-group" aria-label={milestone} key={milestone}>
              <header className="planning-group-heading"><h3>{milestone}</h3><span>{groupTasks.length}</span></header>
              <div className="planning-task-grid">
                {groupTasks.map((task) => (
                  <article className="planning-task-card panel" key={task.id}>
                    <div className="planning-task-top">
                      <span className="planning-task-id">{task.taskId}</span>
                      <span className={`requirement-status status-${task.reviewStatus.toLowerCase()}`}>{task.reviewStatus}</span>
                    </div>
                    <h4>{task.title}</h4>
                    <p className="planning-task-description">{task.description}</p>
                    <div className="planning-task-tags">
                      <span className="planning-tag">{titleCase(task.category)}</span>
                      <span className={`requirement-priority priority-${task.priority.toLowerCase()}`}>{task.priority}</span>
                      <span className="planning-effort"><Clock3 size={13} /> {task.estimatedEffortHours ?? '—'} h</span>
                      {task.demo && <span className="planning-demo-tag">Demo</span>}
                    </div>
                    <div className="planning-task-status">
                      <span className={`task-status-dot task-status-${task.status.toLowerCase()}`} />
                      <span>{titleCase(task.status)}</span>
                      {task.reviewStatus === 'APPROVED' && (
                        <select
                          aria-label={`Update status for ${task.taskId}`}
                          value={task.status}
                          onChange={(event) => void handleStatusChange(task, event.target.value as TaskStatus)}
                        >
                          {statuses.map((status) => <option key={status} value={status}>{titleCase(status)}</option>)}
                        </select>
                      )}
                    </div>
                    <div className="planning-task-actions">
                      <button className="text-button" type="button" onClick={() => setExpandedTaskId(expandedTaskId === task.id ? null : task.id)} aria-expanded={expandedTaskId === task.id}>
                        {expandedTaskId === task.id ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                        {expandedTaskId === task.id ? 'Hide details' : 'Details'}
                      </button>
                      {task.reviewStatus === 'DRAFT' && (
                        <button className="button button-secondary button-small" type="button" onClick={() => beginEdit(task)}><Pencil size={13} /> Edit</button>
                      )}
                      {task.reviewStatus === 'DRAFT' && (
                        <>
                          <button className="button button-secondary button-small" type="button" onClick={() => void handleReview(task, 'approve')}><Check size={13} /> Approve</button>
                          <button className="button button-secondary button-small" type="button" onClick={() => void handleReview(task, 'reject')}><X size={13} /> Reject</button>
                        </>
                      )}
                      {task.reviewStatus !== 'APPROVED' && (
                        <button className="button button-danger button-small" type="button" onClick={() => void handleDelete(task)} aria-label={`Delete ${task.taskId}`}><Trash2 size={13} /></button>
                      )}
                    </div>
                    {expandedTaskId === task.id && <TaskDetails task={task} />}
                  </article>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      {editingTask && (
        <div className="dialog-backdrop" onMouseDown={(event) => event.target === event.currentTarget && !saving && setEditingTask(null)}>
          <section className="project-dialog planning-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="planning-edit-title">
            <div className="dialog-header">
              <div><div className="dialog-icon"><ListTodo size={18} /></div><h2 id="planning-edit-title">Edit {editingTask.taskId}</h2><p>Changes apply only to this draft task.</p></div>
              <button className="icon-button close-dialog" type="button" onClick={() => setEditingTask(null)} disabled={saving} aria-label="Close dialog"><X size={18} /></button>
            </div>
            <form className="project-form planning-edit-form" onSubmit={(event) => void handleSave(event)}>
              <label className="form-field"><span>Title</span><input required maxLength={255} value={editForm.title} onChange={(event) => setEditForm({ ...editForm, title: event.target.value })} /></label>
              <label className="form-field"><span>Description</span><textarea required maxLength={5000} rows={3} value={editForm.description} onChange={(event) => setEditForm({ ...editForm, description: event.target.value })} /></label>
              <div className="form-row">
                <label className="form-field"><span>Category</span><select value={editForm.category} onChange={(event) => setEditForm({ ...editForm, category: event.target.value as TaskCategory })}>{categories.map((category) => <option key={category} value={category}>{titleCase(category)}</option>)}</select></label>
                <label className="form-field"><span>Priority</span><select value={editForm.priority} onChange={(event) => setEditForm({ ...editForm, priority: event.target.value as TaskPriority })}>{priorities.map((priority) => <option key={priority} value={priority}>{titleCase(priority)}</option>)}</select></label>
              </div>
              <div className="form-row">
                <label className="form-field"><span>Effort (hours)</span><input type="number" min={1} max={400} required value={editForm.estimatedEffortHours} onChange={(event) => setEditForm({ ...editForm, estimatedEffortHours: Number(event.target.value) })} /></label>
                <label className="form-field"><span>Milestone</span><input maxLength={100} value={editForm.milestone} onChange={(event) => setEditForm({ ...editForm, milestone: event.target.value })} /></label>
              </div>
              <label className="form-field"><span>Acceptance criteria <small>One per line</small></span><textarea required rows={3} value={editForm.acceptanceCriteria.join('\n')} onChange={(event) => setCriteria(event.target.value)} /></label>
              <ReferencePicker
                title="Approved requirements"
                items={selectableRequirements}
                selectedIds={editForm.relatedRequirementIds}
                onToggle={(id) => toggleSelection('relatedRequirementIds', id)}
              />
              <ReferencePicker
                title="Approved architecture documents"
                items={selectableArchitectures}
                selectedIds={editForm.architectureReferenceIds}
                onToggle={(id) => toggleSelection('architectureReferenceIds', id)}
              />
              <ReferencePicker
                title="Task dependencies"
                items={tasks.filter((task) => task.id !== editingTask.id).map((task) => ({ id: task.id, label: `${task.taskId} · ${task.title}` }))}
                selectedIds={editForm.dependencyIds}
                onToggle={(id) => toggleSelection('dependencyIds', id)}
              />
              {actionError && <p className="form-error" role="alert">{actionError}</p>}
              <div className="dialog-actions">
                <button className="button button-secondary" type="button" onClick={() => setEditingTask(null)} disabled={saving}>Cancel</button>
                <button className="button button-primary" type="submit" disabled={saving || editForm.relatedRequirementIds.length === 0}>
                  {saving ? <LoaderCircle size={15} className="spinning" /> : <Check size={15} />}{saving ? 'Saving...' : 'Save draft'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </section>
  )
}

function TaskDetails({ task }: { task: DevelopmentTask }) {
  return (
    <div className="planning-task-details">
      <div><strong>Acceptance criteria</strong><ul>{task.acceptanceCriteria.length ? task.acceptanceCriteria.map((criterion, index) => <li key={`${task.id}-criterion-${index}`}>{criterion}</li>) : <li>No acceptance criteria saved.</li>}</ul></div>
      <div><strong>Related requirements</strong><ul>{task.relatedRequirements.map((requirement) => <li key={requirement.id}>{requirement.code} · {requirement.title}</li>)}</ul></div>
      <div><strong>Architecture references</strong><ul>{task.architectureReferences.length ? task.architectureReferences.map((architecture) => <li key={architecture.id}>{architecture.title} · v{architecture.version ?? '—'}</li>) : <li>No approved architecture was available when generated.</li>}</ul></div>
      <div><strong>Dependencies</strong><p>{task.dependencyTaskIds.length ? task.dependencyTaskIds.join(', ') : 'None'}</p></div>
      <div><strong>Generation source</strong><p>{task.generationProvider}{task.demo ? ' · Demo output, not AI-generated' : ''}</p></div>
    </div>
  )
}

function ReferencePicker({
  title,
  items,
  selectedIds,
  onToggle,
}: {
  title: string
  items: Array<{ id: string; label: string }>
  selectedIds: string[]
  onToggle: (id: string) => void
}) {
  return (
    <fieldset className="planning-reference-picker">
      <legend>{title}</legend>
      {items.length ? items.map((item) => (
        <label key={item.id}><input type="checkbox" checked={selectedIds.includes(item.id)} onChange={() => onToggle(item.id)} /><span>{item.label}</span></label>
      )) : <p>No available references.</p>}
    </fieldset>
  )
}
