import type { ReactNode } from 'react';

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  // Numeric/measured columns read better right-aligned — the reference
  // console does the same for area/duration/efficiency style columns.
  align?: 'left' | 'right' | 'center';
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  emptyTitle?: string;
  emptyDetail?: string;
  // Leading "No." column. Off by default so existing tables are unchanged.
  indexColumn?: boolean;
  // Row numbering continues across pages (pass page * pageSize).
  indexOffset?: number;
}

function alignClass(align: Column<unknown>['align']): string | undefined {
  if (align === 'right') return 'sakar-table-cell--right';
  if (align === 'center') return 'sakar-table-cell--center';
  return undefined;
}

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  emptyTitle,
  emptyDetail,
  indexColumn,
  indexOffset = 0,
}: DataTableProps<T>) {
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
            {indexColumn && <th className="sakar-table-index">No.</th>}
            {columns.map((col) => (
              <th key={col.key} className={alignClass(col.align)}>
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={rowKey(row)}>
              {indexColumn && <td className="sakar-table-index">{indexOffset + i + 1}</td>}
              {columns.map((col) => (
                <td key={col.key} className={alignClass(col.align)}>
                  {col.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
