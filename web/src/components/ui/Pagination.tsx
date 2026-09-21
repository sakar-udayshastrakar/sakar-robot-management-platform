interface PaginationProps {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  // Optional "Total N" record count, shown alongside the page controls.
  // When omitted, this component behaves exactly as before (hidden entirely
  // for a single page).
  total?: number;
  // Optional rows-per-page control. Both props are required to render it.
  pageSize?: number;
  onPageSizeChange?: (size: number) => void;
  pageSizeOptions?: number[];
}

const DEFAULT_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export function Pagination({
  page,
  totalPages,
  onChange,
  total,
  pageSize,
  onPageSizeChange,
  pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
}: PaginationProps) {
  const showPager = totalPages > 1;
  const showPageSize = pageSize !== undefined && onPageSizeChange !== undefined;
  if (!showPager && total === undefined && !showPageSize) {
    return null;
  }
  return (
    <div className="sakar-pagination">
      {total !== undefined && <span className="sakar-pagination-total">Total {total}</span>}
      {showPageSize && (
        <select
          value={pageSize}
          onChange={(e) => onPageSizeChange(Number(e.target.value))}
          aria-label="Rows per page"
        >
          {pageSizeOptions.map((n) => (
            <option key={n} value={n}>
              {n} / page
            </option>
          ))}
        </select>
      )}
      {showPager && (
        <>
          <button
            type="button"
            className="sakar-btn sakar-btn--secondary"
            disabled={page <= 0}
            onClick={() => onChange(page - 1)}
          >
            Previous
          </button>
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            className="sakar-btn sakar-btn--secondary"
            disabled={page >= totalPages - 1}
            onClick={() => onChange(page + 1)}
          >
            Next
          </button>
        </>
      )}
    </div>
  );
}
