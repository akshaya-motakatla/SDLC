import { useEffect, useId, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
  ArrowLeft,
  Check,
  ChevronDown,
  Clock3,
  LoaderCircle,
  Pencil,
  Save,
  Sparkles,
  X,
} from 'lucide-react'
import { ApiError } from '../services/apiClient'
import {
  approveArchitecture,
  generateArchitecture,
  listArchitectureDocuments,
  rejectArchitecture,
  updateArchitectureDocument,
} from '../services/architectureApi'
import { listRequirements } from '../services/requirementsApi'
import type { ArchitectureDocument, ArchitectureUpdatePayload } from '../types/architecture'
import type { Requirement } from '../types/requirement'
import type { Project } from '../types/project'

interface ArchitectureWorkspaceProps {
  project: Project
  theme: 'light' | 'dark'
  onBack: () => void
  onOpenRequirements: () => void
  onSessionExpired: () => void
}

const diagramLabels: Record<string, string> = {
  systemContext: 'System context',
  components: 'High-level components',
  entityRelationship: 'Entity relationships',
  workflow: 'Architecture generation workflow',
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    return 'Your session has expired. Sign in again to continue.'
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

function editableDocument(document: ArchitectureDocument): ArchitectureUpdatePayload {
  return {
    title: document.title,
    architectureStyle: document.architectureStyle,
    content: document.content,
    diagrams: { ...document.diagrams },
  }
}

export default function ArchitectureWorkspace({
  project,
  theme,
  onBack,
  onOpenRequirements,
  onSessionExpired,
}: ArchitectureWorkspaceProps) {
  const [requirements, setRequirements] = useState<Requirement[]>([])
  const [documents, setDocuments] = useState<ArchitectureDocument[]>([])
  const [selectedRequirementIds, setSelectedRequirementIds] = useState<string[]>([])
  const [selectedDocumentId, setSelectedDocumentId] = useState('')
  const [editForm, setEditForm] = useState<ArchitectureUpdatePayload | null>(null)
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [action, setAction] = useState<'approve' | 'reject' | null>(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([listRequirements(project.id), listArchitectureDocuments(project.id)])
      .then(([loadedRequirements, loadedDocuments]) => {
        if (!active) return
        setRequirements(loadedRequirements)
        setSelectedRequirementIds(
          loadedRequirements.filter((requirement) => requirement.status === 'APPROVED').map((requirement) => requirement.id),
        )
        setDocuments(loadedDocuments)
        setSelectedDocumentId(loadedDocuments[0]?.id ?? '')
        setError('')
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
  const selectedDocument = documents.find((document) => document.id === selectedDocumentId) ?? null
  const currentForm = editForm ?? (selectedDocument ? editableDocument(selectedDocument) : null)

  function updateDocuments(updated: ArchitectureDocument) {
    setDocuments((current) => [
      updated,
      ...current.filter((document) => document.id !== updated.id),
    ].sort((left, right) => right.version - left.version))
    setSelectedDocumentId(updated.id)
  }

  function handleRequestError(requestError: unknown) {
    if (requestError instanceof ApiError && requestError.status === 401) {
      onSessionExpired()
    } else {
      setActionError(errorMessage(requestError))
    }
  }

  async function handleGenerate() {
    setGenerating(true)
    setError('')
    setActionError('')
    try {
      const generated = await generateArchitecture(project.id, { sourceRequirementIds: selectedRequirementIds })
      updateDocuments(generated)
      setEditForm(null)
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

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedDocument || !editForm) return
    setSaving(true)
    setActionError('')
    try {
      const updated = await updateArchitectureDocument(project.id, selectedDocument.id, editForm)
      updateDocuments(updated)
      setEditForm(null)
    } catch (requestError) {
      handleRequestError(requestError)
    } finally {
      setSaving(false)
    }
  }

  async function handleReview(nextAction: 'approve' | 'reject') {
    if (!selectedDocument) return
    setAction(nextAction)
    setActionError('')
    try {
      const updated = nextAction === 'approve'
        ? await approveArchitecture(project.id, selectedDocument.id)
        : await rejectArchitecture(project.id, selectedDocument.id)
      updateDocuments(updated)
    } catch (requestError) {
      handleRequestError(requestError)
    } finally {
      setAction(null)
    }
  }

  function toggleRequirement(requirementId: string) {
    setSelectedRequirementIds((current) => current.includes(requirementId)
      ? current.filter((id) => id !== requirementId)
      : [...current, requirementId])
  }

  return (
    <section className="architecture-workspace" aria-labelledby="architecture-title">
      <button className="text-button requirements-back" type="button" onClick={onBack}>
        <ArrowLeft size={15} /> Back to projects
      </button>
      <header className="requirements-heading">
        <div>
          <span className="eyebrow"><span className="eyebrow-line" /> SOFTWARE ARCHITECTURE</span>
          <h2 id="architecture-title">{project.name}</h2>
          <p>Generate a reviewable design from this project’s approved requirements.</p>
        </div>
        <button className="button button-secondary" type="button" onClick={onOpenRequirements}>
          <ArrowLeft size={15} /> Requirements
        </button>
      </header>

      <section className="project-context panel" aria-label="Project context">
        <div><strong>Project context</strong><span>{project.domain} · {project.preferredStack}</span></div>
        <p>{project.description}</p>
        {project.objectives && <p><strong>Objectives:</strong> {project.objectives}</p>}
      </section>

      <section className="architecture-selector panel" aria-labelledby="approved-requirements-title">
        <div className="architecture-section-heading">
          <div>
            <h3 id="approved-requirements-title">Approved requirements</h3>
            <p>Selected requirements are the only requirements sent as architecture input.</p>
          </div>
          <button
            className="text-button"
            type="button"
            onClick={() => setSelectedRequirementIds(approvedRequirements.map((requirement) => requirement.id))}
            disabled={approvedRequirements.length === 0 || generating}
          >
            Select all
          </button>
        </div>
        {approvedRequirements.length === 0 ? (
          <p className="architecture-empty-note">Approve at least one requirement before generating architecture.</p>
        ) : (
          <div className="architecture-requirements-list">
            {approvedRequirements.map((requirement) => (
              <label className="architecture-requirement-option" key={requirement.id}>
                <input
                  type="checkbox"
                  checked={selectedRequirementIds.includes(requirement.id)}
                  onChange={() => toggleRequirement(requirement.id)}
                  disabled={generating}
                />
                <span><strong>{requirement.code} · {requirement.title}</strong><small>{requirement.description}</small></span>
              </label>
            ))}
          </div>
        )}
        <button
          className="button button-primary"
          type="button"
          onClick={() => void handleGenerate()}
          disabled={loading || generating || selectedRequirementIds.length === 0}
        >
          {generating ? <LoaderCircle size={16} className="spinning" /> : <Sparkles size={16} />}
          {generating ? 'Generating architecture...' : 'Generate Architecture'}
        </button>
      </section>

      {error && <div className="requirements-error" role="alert">{error}</div>}
      {actionError && <div className="requirements-error" role="alert">{actionError}</div>}
      {loading ? (
        <div className="requirements-loading" role="status"><LoaderCircle size={19} className="spinning" /> Loading architecture and requirements...</div>
      ) : documents.length === 0 ? (
        <div className="requirements-empty panel">
          <span className="empty-icon"><Sparkles size={19} /></span>
          <strong>No architecture documents yet</strong>
          <p>Choose approved requirements and generate a draft. It will remain unapproved until you review it.</p>
        </div>
      ) : (
        <>
          <section className="architecture-history panel" aria-labelledby="architecture-history-title">
            <div className="architecture-section-heading">
              <div>
                <h3 id="architecture-history-title">Version history</h3>
                <p>Each generation creates a separate version; approved documents are preserved.</p>
              </div>
              <label className="architecture-version-picker">
                <span className="sr-only">Select architecture version</span>
                <select
                  value={selectedDocumentId}
                  onChange={(event) => {
                    setSelectedDocumentId(event.target.value)
                    setEditForm(null)
                    setActionError('')
                  }}
                  aria-label="Select architecture version"
                >
                  {documents.map((document) => (
                    <option key={document.id} value={document.id}>
                      Version {document.version || 'legacy'} · {document.status}
                    </option>
                  ))}
                </select>
                <ChevronDown size={15} />
              </label>
            </div>
            <div className="architecture-history-items">
              {documents.map((document) => (
                <button
                  className={`architecture-history-item ${document.id === selectedDocumentId ? 'selected' : ''}`}
                  key={document.id}
                  type="button"
                  onClick={() => {
                    setSelectedDocumentId(document.id)
                    setEditForm(null)
                    setActionError('')
                  }}
                  aria-pressed={document.id === selectedDocumentId}
                >
                  <span><strong>Version {document.version || 'legacy'}</strong><small>{document.title}</small></span>
                  <span className={`requirement-status status-${document.status.toLowerCase()}`}>{document.status}</span>
                </button>
              ))}
            </div>
          </section>

          {selectedDocument && currentForm && (
            <article className="architecture-document panel" aria-label={`Architecture version ${selectedDocument.version}`}>
              <header className="architecture-document-header">
                <div>
                  <span className={`requirement-status status-${selectedDocument.status.toLowerCase()}`}>{selectedDocument.status}</span>
                  <h3>{editForm ? 'Edit architecture draft' : selectedDocument.title}</h3>
                  <p><Clock3 size={13} /> Version {selectedDocument.version || 'legacy'} · Updated {new Date(selectedDocument.updatedAt).toLocaleString()}</p>
                </div>
                {!editForm && selectedDocument.status === 'DRAFT' && (
                  <button className="button button-secondary button-small" type="button" onClick={() => setEditForm(editableDocument(selectedDocument))}>
                    <Pencil size={14} /> Edit draft
                  </button>
                )}
              </header>
              {selectedDocument.demo && (
                <p className="provider-notice provider-demo" role="status">
                  Demo output — generated by the local mock provider, not an external AI model.
                </p>
              )}
              {selectedDocument.provider === 'OPENAI' && (
                <p className="provider-notice" role="status">Generated by the configured OpenAI provider.</p>
              )}

              {editForm ? (
                <form className="architecture-edit-form" onSubmit={(event) => void handleSave(event)}>
                  <label className="form-field"><span>Document title</span><input required maxLength={255} value={editForm.title} onChange={(event) => setEditForm({ ...editForm, title: event.target.value })} /></label>
                  <label className="form-field"><span>Architecture style</span><input required maxLength={120} value={editForm.architectureStyle} onChange={(event) => setEditForm({ ...editForm, architectureStyle: event.target.value })} /></label>
                  <label className="form-field"><span>Architecture content <small>Keep all required section headings</small></span><textarea required rows={22} maxLength={50_000} value={editForm.content} onChange={(event) => setEditForm({ ...editForm, content: event.target.value })} /></label>
                  <div className="architecture-diagrams">
                    {Object.entries(diagramLabels).map(([name, label]) => (
                      <label className="form-field" key={name}>
                        <span>{label} Mermaid source</span>
                        <textarea
                          required
                          rows={7}
                          maxLength={12_000}
                          value={editForm.diagrams[name] ?? ''}
                          onChange={(event) => setEditForm({
                            ...editForm,
                            diagrams: { ...editForm.diagrams, [name]: event.target.value },
                          })}
                        />
                      </label>
                    ))}
                  </div>
                  <div className="requirement-actions">
                    <button className="button button-secondary button-small" type="button" onClick={() => setEditForm(null)} disabled={saving}><X size={14} /> Cancel</button>
                    <button className="button button-primary button-small" type="submit" disabled={saving}>
                      {saving ? <LoaderCircle size={14} className="spinning" /> : <Save size={14} />}
                      {saving ? 'Saving...' : 'Save draft'}
                    </button>
                  </div>
                </form>
              ) : (
                <>
                  <div className="architecture-style"><strong>Recommended style</strong><span>{selectedDocument.architectureStyle}</span></div>
                  <div className="architecture-source-requirements" aria-label="Source approved requirements">
                    <strong>Based on approved requirements</strong>
                    <div className="architecture-source-chips">
                      {selectedDocument.sourceRequirementIds.map((id) => {
                        const requirement = requirements.find((item) => item.id === id)
                        return <span className="requirement-code" key={id}>{requirement ? `${requirement.code} · ${requirement.title}` : id}</span>
                      })}
                    </div>
                  </div>
                  <div className="architecture-content">{selectedDocument.content}</div>
                  <section className="architecture-diagrams" aria-label="Architecture diagrams">
                    {Object.entries(diagramLabels).map(([name, label]) => {
                      const source = selectedDocument.diagrams[name]
                      return source ? (
                        <DiagramPanel key={`${selectedDocument.id}-${name}`} name={name} title={label} source={source} theme={theme} />
                      ) : (
                        <div className="architecture-diagram panel" key={name}>
                          <h4>{label}</h4>
                          <p className="architecture-empty-note">No valid source is available for this legacy diagram.</p>
                        </div>
                      )
                    })}
                  </section>
                  {selectedDocument.status === 'DRAFT' && (
                    <div className="architecture-review-actions">
                      <button className="button button-secondary" type="button" onClick={() => void handleReview('reject')} disabled={action !== null}>
                        {action === 'reject' ? <LoaderCircle size={15} className="spinning" /> : <X size={15} />} Reject
                      </button>
                      <button className="button button-primary" type="button" onClick={() => void handleReview('approve')} disabled={action !== null}>
                        {action === 'approve' ? <LoaderCircle size={15} className="spinning" /> : <Check size={15} />} Approve architecture
                      </button>
                    </div>
                  )}
                  {selectedDocument.status !== 'DRAFT' && (
                    <p className="architecture-immutable-note" role="status">
                      This reviewed version is immutable. Generate a new version to make further changes.
                    </p>
                  )}
                </>
              )}
            </article>
          )}
        </>
      )}
    </section>
  )
}

function DiagramPanel({
  name,
  title,
  source,
  theme,
}: {
  name: string
  title: string
  source: string
  theme: 'light' | 'dark'
}) {
  const renderId = useId().replace(/:/g, '')
  const previewRef = useRef<HTMLDivElement>(null)
  const [showSource, setShowSource] = useState(false)
  const [renderFailure, setRenderFailure] = useState<{ source: string; message: string } | null>(null)
  const renderError = renderFailure?.source === source ? renderFailure.message : ''

  useEffect(() => {
    let active = true
    const preview = previewRef.current
    preview?.replaceChildren()
    void import('mermaid')
      .then(({ default: mermaid }) => {
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict',
          theme: theme === 'dark' ? 'dark' : 'default',
          htmlLabels: false,
        })
        return mermaid.parse(source).then(() => mermaid.render(`diagram-${renderId}-${name}`, source))
      })
      .then(({ svg }) => {
        if (!active || !preview) return
        const parsed = new DOMParser().parseFromString(svg, 'image/svg+xml')
        if (parsed.querySelector('parsererror') || parsed.documentElement.localName !== 'svg') {
          throw new Error('The diagram renderer returned invalid SVG.')
        }
        preview.replaceChildren(document.importNode(parsed.documentElement, true))
      })
      .catch(() => {
        if (active) {
          setRenderFailure({
            source,
            message: 'This Mermaid source could not be rendered. The preview is hidden; review or edit the source before saving.',
          })
        }
      })
    return () => {
      active = false
      preview?.replaceChildren()
    }
  }, [name, renderId, source, theme])

  return (
    <section className="architecture-diagram panel" aria-labelledby={`diagram-title-${renderId}`}>
      <div className="architecture-diagram-heading">
        <h4 id={`diagram-title-${renderId}`}>{title}</h4>
        <button className="text-button" type="button" onClick={() => setShowSource((current) => !current)} aria-expanded={showSource}>
          {showSource ? 'Hide source' : 'View Mermaid source'}
        </button>
      </div>
      {renderError ? <p className="architecture-diagram-error" role="alert">{renderError}</p> : <div className="mermaid-preview" ref={previewRef} role="img" aria-label={`${title} diagram preview`} />}
      {showSource && <pre className="mermaid-source"><code>{source}</code></pre>}
    </section>
  )
}
