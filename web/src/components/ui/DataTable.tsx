import type { ReactNode } from 'react';
import { EmptyState } from './States';

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
  if (rows.length === 0) {
    return <EmptyState title={emptyTitle ?? 'No records'} detail={emptyDetail} />;
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
