import type { ReactNode } from 'react';

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  emptyTitle?: string;
  emptyDetail?: string;
}

export function DataTable<T>({ columns, rows, rowKey, emptyTitle, emptyDetail }: DataTableProps<T>) {
  // Plain centered text, no icon — verified against the live Keenon Cloud
  // Robot Detail "Task management" table, whose empty state is just
  // "No Data" with no illustration (a different, more minimal treatment
  // than the icon-bearing EmptyState used for whole-panel/widget empties
  // elsewhere). Keeps Sakar's more specific existing copy (emptyTitle),
  // just drops the icon to match a table's own empty-state convention.
  if (rows.length === 0) {
    return (
      <div className="sakar-table-empty">
        <span className="sakar-table-empty-title">{emptyTitle ?? 'No records'}</span>
        {emptyDetail && <span className="sakar-table-empty-detail">{emptyDetail}</span>}
      </div>
    );
  }

  return (
    <div className="sakar-table-wrap">
      <table className="sakar-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key}>{col.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((col) => (
                <td key={col.key}>{col.render(row)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
