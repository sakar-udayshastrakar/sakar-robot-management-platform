import { useState } from 'react';
import { listCleaningHistory } from '../../api/cleaning';
import { useApi } from '../../hooks/useApi';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { Pagination } from '../../components/ui/Pagination';
import { LoadingState, ErrorState } from '../../components/ui/States';

function formatDuration(seconds: number | null): string {
  if (seconds === null) return '—';
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return minutes > 0 ? `${minutes}m ${rest}s` : `${rest}s`;
}

// Read-only real cleaning history (GET /robots/{id}/cleaning/history). A row
// only ever exists here because the operator actually completed a real
// CLEANING-type task through the Tasks tab — see CleaningSessionService's
// Javadoc; there is no create/edit path to wire up. Vendor-specific
// identifiers (vendorReference) are never surfaced to normal users here —
// Sakar terminology only.
export function RobotCleaningPanel({ robotId }: { robotId: string }) {
  const [page, setPage] = useState(0);
  const { data, status, error, refetch } = useApi(() => listCleaningHistory(robotId, page, 10), [robotId, page]);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading cleaning history…" />;
  }
  if (status === 'error') {
    return (
      <ErrorState
        title="Could not load cleaning history"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <Card title={`Cleaning History (${data?.totalElements ?? 0})`}>
      <DataTable
        rows={data?.content ?? []}
        rowKey={(s) => s.id}
        emptyTitle="No completed cleaning sessions yet"
        emptyDetail="A session appears here once a CLEANING-type task is created and stopped from the Tasks tab."
        columns={[
          { key: 'start', header: 'Start Time', render: (s) => new Date(s.startedAt).toLocaleString() },
          { key: 'end', header: 'End Time', render: (s) => (s.endedAt ? new Date(s.endedAt).toLocaleString() : '—') },
          { key: 'duration', header: 'Duration', render: (s) => formatDuration(s.durationSeconds) },
          { key: 'area', header: 'Cleaned Area', render: (s) => (s.areaSqMeters !== null ? `${s.areaSqMeters} m²` : 'Not available') },
          { key: 'efficiency', header: 'Efficiency', render: (s) => (s.efficiency !== null ? `${s.efficiency}%` : 'Not available') },
          { key: 'result', header: 'Result', render: (s) => s.result ?? '—' },
          { key: 'failure', header: 'Failure Reason', render: (s) => s.failureReason ?? '—' },
        ]}
      />
      {data && <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />}
    </Card>
  );
}
