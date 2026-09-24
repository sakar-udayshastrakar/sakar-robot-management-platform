import { useNavigate } from 'react-router-dom';
import { getRetentionAnalytics } from '../../api/dashboard';
import { useApi } from '../../hooks/useApi';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';
import { LoadingState, ErrorState } from '../../components/ui/States';
import type { RetentionRow } from '../../types/domain';

function downloadCsv(filename: string, header: string[], rows: string[][]) {
  const csv = [header.join(','), ...rows.map((r) => r.map((v) => `"${v}"`).join(','))].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

// Operational Dashboard → Use Retention Analytics. Every count here is
// derived from real robot_tasks.created_at rows — a store counts as
// "consecutively used" for N days ending on a date only if it has at least
// one task on each of those N days, and "consecutively unused" only if it
// has none — never simulated (see RetentionRow's own comment, types/domain.ts).
export function RetentionAnalyticsPage() {
  const navigate = useNavigate();
  const { data: rows, status, error, refetch } = useApi(() => getRetentionAnalytics(8), []);

  if (status === 'loading' || status === 'idle') {
    return <LoadingState title="Loading retention analytics…" />;
  }
  if (status === 'error' || !rows) {
    return (
      <ErrorState
        title="Could not load retention analytics"
        detail={error ?? undefined}
        action={<button type="button" className="sakar-btn sakar-btn--secondary" onClick={refetch}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb items={[{ label: 'Use Retention Analytics' }]} />
      <button type="button" className="sakar-btn sakar-btn--primary" style={{ marginBottom: 16 }} onClick={() => navigate('/operational-dashboard')}>Back</button>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <Card
          title="Number of continuously used stores"
          actions={(
            <button
              type="button"
              className="sakar-btn sakar-btn--primary sakar-btn--sm"
              onClick={() => downloadCsv(
                'continuously-used-stores.csv',
                ['Date', '3 consecutive days', '7 consecutive days', '15 consecutive days'],
                rows.map((r) => [r.date, String(r.used3), String(r.used7), String(r.used15)]),
              )}
            >
              Export
            </button>
          )}
        >
          <DataTable
            rows={rows}
            rowKey={(r) => r.date}
            emptyTitle="No Data"
            columns={[
              { key: 'date', header: 'Date', render: (r: RetentionRow) => r.date },
              { key: 'used3', header: '3 consecutive days', render: (r: RetentionRow) => r.used3 },
              { key: 'used7', header: '7 consecutive days', render: (r: RetentionRow) => r.used7 },
              { key: 'used15', header: '15 consecutive days', render: (r: RetentionRow) => r.used15 },
            ]}
          />
        </Card>

        <Card
          title="Number of consecutive unused stores"
          actions={(
            <button
              type="button"
              className="sakar-btn sakar-btn--primary sakar-btn--sm"
              onClick={() => downloadCsv(
                'consecutive-unused-stores.csv',
                ['Date', 'Not used for 3 consecutive days', 'Not used for 7 consecutive days', 'Not used for 15 consecutive days'],
                rows.map((r) => [r.date, String(r.unused3), String(r.unused7), String(r.unused15)]),
              )}
            >
              Export
            </button>
          )}
        >
          <DataTable
            rows={rows}
            rowKey={(r) => r.date}
            emptyTitle="No Data"
            columns={[
              { key: 'date', header: 'Date', render: (r: RetentionRow) => r.date },
              { key: 'unused3', header: 'Not used for 3 consecutive days', render: (r: RetentionRow) => r.unused3 },
              { key: 'unused7', header: 'Not used for 7 consecutive days', render: (r: RetentionRow) => r.unused7 },
              { key: 'unused15', header: 'Not used for 15 consecutive days', render: (r: RetentionRow) => r.unused15 },
            ]}
          />
        </Card>
      </div>
    </div>
  );
}
