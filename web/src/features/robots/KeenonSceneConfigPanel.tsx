import { useEffect, useState, type FormEvent } from 'react';
import { getSceneConfig, setSceneConfig } from '../../api/keenon';
import { useApi } from '../../hooks/useApi';
import { usePermissions } from '../../hooks/usePermissions';
import { useToast } from '../../components/ui/Toast';
import { ApiRequestError } from '../../api/client';
import { Card } from '../../components/ui/Card';
import { LoadingState, ErrorState, EmptyState } from '../../components/ui/States';

// Sakar-owned per-robot Keenon sceneCode configuration (GET/PUT
// .../keenon/scene-config) — never a live Keenon read (see the backend's
// KeenonMapMetadataSyncService Javadoc). This is the prerequisite step for
// the Map tab's image/metadata to resolve at all for a KEENON_CLOUD robot:
// without a configured sceneCode here, RobotMapPanel's Map card stays in
// its "Map data unavailable" state indefinitely.
export function KeenonSceneConfigPanel({ robotId }: { robotId: string }) {
  const { hasPermission } = usePermissions();
  const { data, status, errorStatus, error, refetch } = useApi(() => getSceneConfig(robotId), [robotId]);

  const canConfigure = hasPermission('ROBOT_CONFIGURE');

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading Keenon scene configuration…" />;
  }

  if (status === 'error') {
    if (errorStatus === 422) {
      return (
        <Card title="Keenon Scene Configuration">
          <EmptyState
            title="Not available for this robot"
            detail="Keenon scene configuration only applies to KEENON_CLOUD robots — this robot model does not use that integration."
          />
        </Card>
      );
    }
    if (errorStatus === 404) {
      return (
        <Card title="Keenon Scene Configuration">
          <EmptyState
            title="Not configured yet"
            detail="This robot has no Keenon sceneCode configured yet. Map metadata/image sync cannot resolve a scene for it until one is set below."
          />
          {canConfigure && <SceneConfigForm robotId={robotId} onSaved={refetch} />}
        </Card>
      );
    }
    return (
      <Card title="Keenon Scene Configuration">
        <ErrorState
          title="Could not load scene configuration"
          detail={error ?? undefined}
          action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
        />
      </Card>
    );
  }

  return (
    <Card title="Keenon Scene Configuration">
      <div className="sakar-fact-group" style={{ marginBottom: 16 }}>
        <span className="sakar-kv-row">
          <span className="sakar-kv-label">Scene code:</span>
          <span className="sakar-kv-value sakar-mono">{data!.sceneCode}</span>
        </span>
        <span className="sakar-kv-row">
          <span className="sakar-kv-label">Scene name:</span>
          <span className="sakar-kv-value">{data!.sceneName ?? '—'}</span>
        </span>
      </div>
      {canConfigure && (
        <SceneConfigForm
          robotId={robotId}
          initialSceneCode={data!.sceneCode}
          initialSceneName={data!.sceneName ?? ''}
          onSaved={refetch}
        />
      )}
    </Card>
  );
}

interface SceneConfigFormProps {
  robotId: string;
  initialSceneCode?: string;
  initialSceneName?: string;
  onSaved: () => void;
}

function SceneConfigForm({ robotId, initialSceneCode = '', initialSceneName = '', onSaved }: SceneConfigFormProps) {
  const toast = useToast();
  const [sceneCode, setSceneCodeInput] = useState(initialSceneCode);
  const [sceneName, setSceneNameInput] = useState(initialSceneName);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Keep the form in sync if the underlying configuration changes (e.g. a
  // successful save refetches and this component is reused, not remounted).
  useEffect(() => {
    setSceneCodeInput(initialSceneCode);
    setSceneNameInput(initialSceneName);
  }, [initialSceneCode, initialSceneName]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setValidationError(null);
    setSubmitError(null);

    const trimmedCode = sceneCode.trim();
    const trimmedName = sceneName.trim();
    if (!trimmedCode) {
      setValidationError('Scene code is required.');
      return;
    }

    setSubmitting(true);
    try {
      await setSceneConfig(robotId, { sceneCode: trimmedCode, sceneName: trimmedName ? trimmedName : null });
      toast.show('Keenon scene configuration saved', 'success');
      onSaved();
    } catch (err) {
      setSubmitError(err instanceof ApiRequestError ? err.message : 'Failed to save scene configuration');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <div className="sakar-field">
        <label htmlFor="scene-code">Scene code</label>
        <input
          id="scene-code"
          value={sceneCode}
          onChange={(e) => setSceneCodeInput(e.target.value)}
        />
      </div>
      <div className="sakar-field">
        <label htmlFor="scene-name">Scene name (optional)</label>
        <input
          id="scene-name"
          value={sceneName}
          onChange={(e) => setSceneNameInput(e.target.value)}
        />
      </div>
      {validationError && <div className="sakar-field-error" style={{ marginBottom: 12 }}>{validationError}</div>}
      {submitError && <div className="sakar-field-error" style={{ marginBottom: 12 }}>{submitError}</div>}
      <button type="submit" className="sakar-btn sakar-btn--primary" disabled={submitting}>
        {submitting ? 'Saving…' : 'Save scene configuration'}
      </button>
    </form>
  );
}
