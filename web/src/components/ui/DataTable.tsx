import { Fragment, useState, type ReactNode } from 'react';
import { ErrorState } from './States';

export type SortDirection = 'asc' | 'desc';

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  // Numeric/measured columns read better right-aligned.
  align?: 'left' | 'right' | 'center';
  // Marks the header clickable. Sorting itself stays the page's job — this
  // component only renders the affordance and reports the intent.
  sortable?: boolean;
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
  // Skeleton rows in place of data while a request is in flight.
  loading?: boolean;
  skeletonRows?: number;
  // Inline error surface, so a failed fetch can keep the page's shape.
  error?: string | null;
  onRetry?: () => void;
  // Sorting is controlled by the caller: it owns both the order and the state.
  sortKey?: string;
  sortDirection?: SortDirection;
  onSort?: (key: string, direction: SortDirection) => void;
  // Supplying this adds an expander column and an extra row when open.
  renderExpanded?: (row: T) => ReactNode;
  // Selection foundation — controlled; nothing is selected unless the caller
  // passes both selectedKeys and onSelectionChange.
  selectable?: boolean;
  selectedKeys?: string[];
  onSelectionChange?: (keys: string[]) => void;
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
  loading,
  skeletonRows = 5,
  error,
  onRetry,
  sortKey,
  sortDirection,
  onSort,
  renderExpanded,
  selectable,
  selectedKeys,
  onSelectionChange,
}: DataTableProps<T>) {
  const [expanded, setExpanded] = useState<string[]>([]);

  if (error) {
    return (
      <ErrorState
        title="Could not load this table"
        detail={error}
        action={
          onRetry ? (
            <button type="button" className="sakar-btn sakar-btn--secondary" onClick={onRetry}>
              Retry
            </button>
          ) : undefined
        }
      />
    );
  }

  // Plain centered text, no icon — a table's own empty-state convention,
  // distinct from the icon-bearing EmptyState used for whole-panel empties.
  if (!loading && rows.length === 0) {
    return (
      <div className="sakar-table-empty">
        <span className="sakar-table-empty-title">{emptyTitle ?? 'No records'}</span>
        {emptyDetail && <span className="sakar-table-empty-detail">{emptyDetail}</span>}
      </div>
    );
  }

  const selected = selectedKeys ?? [];
  const canSelect = Boolean(selectable && onSelectionChange);
  const leadingCols = (selectable ? 1 : 0) + (renderExpanded ? 1 : 0) + (indexColumn ? 1 : 0);
  const totalCols = leadingCols + columns.length;

  const allKeys = rows.map(rowKey);
  const allSelected = allKeys.length > 0 && allKeys.every((k) => selected.includes(k));

  function toggleAll() {
    onSelectionChange?.(allSelected ? [] : allKeys);
  }

  function toggleOne(key: string) {
    onSelectionChange?.(selected.includes(key) ? selected.filter((k) => k !== key) : [...selected, key]);
  }

  function toggleExpanded(key: string) {
    setExpanded((prev) => (prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]));
  }

  function headerSortProps(col: Column<T>) {
    if (!col.sortable || !onSort) return {};
    const isActive = sortKey === col.key;
    const next: SortDirection = isActive && sortDirection === 'asc' ? 'desc' : 'asc';
    return {
      className: [alignClass(col.align), 'sakar-table-sortable'].filter(Boolean).join(' '),
      onClick: () => onSort(col.key, next),
      'aria-sort': (isActive ? (sortDirection === 'desc' ? 'descending' : 'ascending') : 'none') as
        | 'ascending'
        | 'descending'
        | 'none',
    };
  }

  return (
    <div className="sakar-table-wrap">
      <table className="sakar-table">
        <thead>
          <tr>
            {selectable && (
              <th className="sakar-table-select">
                <input
                  type="checkbox"
                  checked={allSelected}
                  onChange={toggleAll}
                  disabled={!canSelect}
                  aria-label="Select all rows"
                />
              </th>
            )}
            {renderExpanded && <th className="sakar-table-expander" />}
            {indexColumn && <th className="sakar-table-index">No.</th>}
            {columns.map((col) => {
              const sortProps = headerSortProps(col);
              const isActive = sortKey === col.key;
              return (
                <th key={col.key} className={alignClass(col.align)} {...sortProps}>
                  {col.header}
                  {col.sortable && onSort && (
                    <span
                      className={'sakar-sort-indicator' + (isActive ? ' sakar-sort-indicator--active' : '')}
                      aria-hidden="true"
                    >
                      {isActive ? (sortDirection === 'desc' ? '▼' : '▲') : '▲▼'}
                    </span>
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {loading
            ? Array.from({ length: skeletonRows }, (_, i) => (
                <tr key={`skeleton-${i}`} className="sakar-table-skeleton-row">
                  {Array.from({ length: totalCols }, (_, c) => (
                    <td key={c}>
                      <div className="sakar-skeleton" />
                    </td>
                  ))}
                </tr>
              ))
            : rows.map((row, i) => {
                const key = rowKey(row);
                const isOpen = expanded.includes(key);
                return (
                  <Fragment key={key}>
                    <tr>
                      {selectable && (
                        <td className="sakar-table-select">
                          <input
                            type="checkbox"
                            checked={selected.includes(key)}
                            onChange={() => toggleOne(key)}
                            disabled={!canSelect}
                            aria-label={`Select row ${i + 1}`}
                          />
                        </td>
                      )}
                      {renderExpanded && (
                        <td className="sakar-table-expander">
                          <button
                            type="button"
                            className={'sakar-expander-btn' + (isOpen ? ' sakar-expander-btn--open' : '')}
                            onClick={() => toggleExpanded(key)}
                            aria-expanded={isOpen}
                            aria-label={isOpen ? 'Collapse row' : 'Expand row'}
                          >
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth={2}>
                              <path d="M9 18l6-6-6-6" />
                            </svg>
                          </button>
                        </td>
                      )}
                      {indexColumn && <td className="sakar-table-index">{indexOffset + i + 1}</td>}
                      {columns.map((col) => (
                        <td key={col.key} className={alignClass(col.align)}>
                          {col.render(row)}
                        </td>
                      ))}
                    </tr>
                    {renderExpanded && isOpen && (
                      <tr className="sakar-table-expanded-row">
                        <td colSpan={totalCols}>{renderExpanded(row)}</td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
        </tbody>
      </table>
    </div>
  );
}
