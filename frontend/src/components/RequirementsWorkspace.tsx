import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowLeft, Check, LoaderCircle, Pencil, Sparkles, Trash2, X } from 'lucide-react'
import { ApiError } from '../services/apiClient'
import {
  approveRequirement,
  deleteRequirement,
  generateRequirements,
  listRequirements,
  rejectRequirement,
  updateRequirement,
} from '../services/requirementsApi'
import type { Project } from '../types/project'
import type {
  Requirement,
  RequirementPriority,
  RequirementType,
  RequirementUpdatePayload,
} from '../types/requirement'

interface RequirementsWorkspaceProps {
  project: Project
  onBack: () => void
  onSessionExpired: () => void
}

const emptyEdit: RequirementUpdatePayload = {
  title: '',
  description: '',
  type: 'FUNCTIONAL',
  priority: 'MEDIUM',
  acceptanceCriteria: [],
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    return 'Your session has expired. Sign in again to continue.'
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

export default function RequirementsWorkspace({ project, onBack, onSessionExpired }: RequirementsWorkspaceProps) {
  const [requirements, setRequirements] = useState<Requirement[]>([])
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [providerLabel, setProviderLabel] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editForm, setEditForm] = useState<RequirementUpdatePayload>(emptyEdit)
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    let active = true
    void listRequirements(project.id)
      .then((items) => {
        if (active) setRequirements(items)
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

  const groups = useMemo(() => ({
    FUNCTIONAL: requirements.filter((requirement) => requirement.type === 'FUNCTIONAL'),
    NON_FUNCTIONAL: requirements.filter((requirement) => requirement.type === 'NON_FUNCTIONAL'),
  }), [requirements])

  async function handleGenerate() {
    setGenerating(true)
    setError('')
    try {
      const result = await generateRequirements(project.id)
      setRequirements((current) => [...result.requirements, ...current])
      setProviderLabel(result.demo ? `${result.provider} · Demo output, not AI-generated` : result.provider)
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        onSessionExpired()
      } else {
        setError(errorMessage(requestError))
      }
    } finally {
      setGenerating(false)
    }
  }

  function beginEdit(requirement: Requirement) {
    setEditingId(requirement.id)
    setActionError('')
    setEditForm({
      title: requirement.title,
      description: requirement.description,
      type: requirement.type,
      priority: requirement.priority,
      acceptanceCriteria: requirement.acceptanceCriteria,
    })
  }

  async function handleEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editingId) return
    setSaving(true)
    setActionError('')
    try {
      const updated = await updateRequirement(project.id, editingId, editForm)
      setRequirements((current) => current.map((item) => item.id === updated.id ? updated : item))
      setEditingId(null)
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        onSessionExpired()
      } else {
        setActionError(errorMessage(requestError))
      }
    } finally {
      setSaving(false)
    }
  }

  async function handleStatusAction(requirement: Requirement, action: 'approve' | 'reject') {
    setActionError('')
    try {
      const updated = action === 'approve'
        ? await approveRequirement(project.id, requirement.id)
        : await rejectRequirement(project.id, requirement.id)
      setRequirements((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        onSessionExpired()
      } else {
        setActionError(errorMessage(requestError))
      }
    }
  }

  async function handleDelete(requirement: Requirement) {
    const confirmed = window.confirm(`Delete "${requirement.title}"? This action cannot be undone.`)
    if (!confirmed) return
    setActionError('')
    try {
      await deleteRequirement(project.id, requirement.id)
      setRequirements((current) => current.filter((item) => item.id !== requirement.id))
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        onSessionExpired()
      } else {
        setActionError(errorMessage(requestError))
      }
    }
  }

  function setAcceptanceCriteria(value: string) {
    setEditForm((current) => ({
      ...current,
      acceptanceCriteria: value.split('\n').map((criterion) => criterion.trim()).filter(Boolean),
    }))
  }

  return (
    <section className="requirements-workspace" aria-labelledby="requirements-title">
      <button className="text-button requirements-back" type="button" onClick={onBack}>
        <ArrowLeft size={15} /> Back to projects
      </button>
      <header className="requirements-heading">
        <div>
          <span className="eyebrow"><span className="eyebrow-line" /> REQUIREMENTS ENGINEERING</span>
          <h2 id="requirements-title">{project.name}</h2>
          <p>Generate, refine, and review requirements using this saved project context.</p>
        </div>
        <button className="button button-primary" type="button" onClick={() => void handleGenerate()} disabled={generating || loading}>
          {generating ? <LoaderCircle size={16} className="spinning" /> : <Sparkles size={16} />}
          {generating ? 'Generating...' : 'Generate Requirements'}
        </button>
      </header>

      <section className="project-context panel" aria-label="Project context">
        <div><strong>Project context</strong><span>{project.domain} · {project.preferredStack}</span></div>
        <p>{project.description}</p>
        {project.objectives && <p><strong>Objectives:</strong> {project.objectives}</p>}
      </section>

      {providerLabel && (
        <p className={`provider-notice ${providerLabel.includes('Demo output') ? 'provider-demo' : ''}`} role="status">
          Generation source: <strong>{providerLabel}</strong>
        </p>
      )}
      {error && <div className="requirements-error" role="alert">{error}</div>}
      {actionError && <div className="requirements-error" role="alert">{actionError}</div>}
      {loading ? (
        <div className="requirements-loading" role="status"><LoaderCircle size={19} className="spinning" /> Loading saved requirements...</div>
      ) : requirements.length === 0 ? (
        <div className="requirements-empty panel">
          <span className="empty-icon"><Sparkles size={19} /></span>
          <strong>No requirements yet</strong>
          <p>Generate a first set for this project. Generated items remain pending until you review them.</p>
          <button className="button button-primary button-small" type="button" onClick={() => void handleGenerate()} disabled={generating}>
            <Sparkles size={15} /> Generate Requirements
          </button>
        </div>
      ) : (
        <div className="requirement-groups">
          <RequirementGroup
            title="Functional requirements"
            items={groups.FUNCTIONAL}
            editingId={editingId}
            editForm={editForm}
            saving={saving}
            onEdit={beginEdit}
            onEditSubmit={handleEdit}
            onEditCancel={() => setEditingId(null)}
            onEditFormChange={setEditForm}
            onCriteriaChange={setAcceptanceCriteria}
            onStatusAction={handleStatusAction}
            onDelete={handleDelete}
          />
          <RequirementGroup
            title="Non-functional requirements"
            items={groups.NON_FUNCTIONAL}
            editingId={editingId}
            editForm={editForm}
            saving={saving}
            onEdit={beginEdit}
            onEditSubmit={handleEdit}
            onEditCancel={() => setEditingId(null)}
            onEditFormChange={setEditForm}
            onCriteriaChange={setAcceptanceCriteria}
            onStatusAction={handleStatusAction}
            onDelete={handleDelete}
          />
        </div>
      )}
    </section>
  )
}

interface RequirementGroupProps {
  title: string
  items: Requirement[]
  editingId: string | null
  editForm: RequirementUpdatePayload
  saving: boolean
  onEdit: (requirement: Requirement) => void
  onEditSubmit: (event: FormEvent<HTMLFormElement>) => void
  onEditCancel: () => void
  onEditFormChange: (form: RequirementUpdatePayload) => void
  onCriteriaChange: (value: string) => void
  onStatusAction: (requirement: Requirement, action: 'approve' | 'reject') => void
  onDelete: (requirement: Requirement) => void
}

function RequirementGroup({
  title,
  items,
  editingId,
  editForm,
  saving,
  onEdit,
  onEditSubmit,
  onEditCancel,
  onEditFormChange,
  onCriteriaChange,
  onStatusAction,
  onDelete,
}: RequirementGroupProps) {
  return (
    <section className="requirement-group" aria-label={title}>
      <div className="requirement-group-heading"><h3>{title}</h3><span>{items.length}</span></div>
      {items.length === 0 ? (
        <div className="requirement-group-empty panel">No {title.toLowerCase()} have been generated.</div>
      ) : items.map((requirement) => (
        <article className="requirement-card panel" key={requirement.id}>
          {editingId === requirement.id ? (
            <form className="requirement-edit-form" onSubmit={(event) => void onEditSubmit(event)}>
              <label className="form-field"><span>Title</span><input required maxLength={255} value={editForm.title} onChange={(event) => onEditFormChange({ ...editForm, title: event.target.value })} /></label>
              <label className="form-field"><span>Description</span><textarea required rows={3} value={editForm.description} onChange={(event) => onEditFormChange({ ...editForm, description: event.target.value })} /></label>
              <div className="form-row">
                <label className="form-field"><span>Type</span><select value={editForm.type} onChange={(event) => onEditFormChange({ ...editForm, type: event.target.value as RequirementType })}><option value="FUNCTIONAL">Functional</option><option value="NON_FUNCTIONAL">Non-functional</option></select></label>
                <label className="form-field"><span>Priority</span><select value={editForm.priority} onChange={(event) => onEditFormChange({ ...editForm, priority: event.target.value as RequirementPriority })}><option value="HIGH">High</option><option value="MEDIUM">Medium</option><option value="LOW">Low</option></select></label>
              </div>
              <label className="form-field"><span>Acceptance criteria <small>One per line</small></span><textarea required rows={4} value={editForm.acceptanceCriteria.join('\n')} onChange={(event) => onCriteriaChange(event.target.value)} /></label>
              <div className="requirement-actions">
                <button className="button button-secondary button-small" type="button" onClick={onEditCancel} disabled={saving}><X size={14} /> Cancel</button>
                <button className="button button-primary button-small" type="submit" disabled={saving}>{saving ? <LoaderCircle size={14} className="spinning" /> : <Check size={14} />}{saving ? 'Saving...' : 'Save changes'}</button>
              </div>
            </form>
          ) : (
            <>
              <div className="requirement-card-top">
                <div><span className="requirement-code">{requirement.code}</span><span className={`requirement-priority priority-${requirement.priority.toLowerCase()}`}>{requirement.priority}</span></div>
                <span className={`requirement-status status-${requirement.status.toLowerCase()}`}>{requirement.status}</span>
              </div>
              <h4>{requirement.title}</h4>
              <p className="requirement-description">{requirement.description}</p>
              <div className="acceptance-criteria">
                <strong>Acceptance criteria</strong>
                <ul>{requirement.acceptanceCriteria.map((criterion, index) => <li key={`${requirement.id}-${index}`}>{criterion}</li>)}</ul>
              </div>
              <div className="requirement-card-footer">
                <span>Source: {requirement.source}</span>
                <div className="requirement-actions">
                  <button className="button button-secondary button-small" type="button" onClick={() => onEdit(requirement)}><Pencil size={14} /> Edit</button>
                  {requirement.status !== 'APPROVED' && <button className="button button-secondary button-small" type="button" onClick={() => void onStatusAction(requirement, 'approve')}><Check size={14} /> Approve</button>}
                  {requirement.status !== 'REJECTED' && <button className="button button-secondary button-small" type="button" onClick={() => void onStatusAction(requirement, 'reject')}><X size={14} /> Reject</button>}
                  <button className="button button-danger button-small" type="button" onClick={() => void onDelete(requirement)} aria-label={`Delete ${requirement.title}`}><Trash2 size={14} /></button>
                </div>
              </div>
            </>
          )}
        </article>
      ))}
    </section>
  )
}
