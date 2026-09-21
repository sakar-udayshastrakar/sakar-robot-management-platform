interface PaginationProps {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  // Optional "Total N" record count, shown alongside the page controls the
  // way the reference console does. When omitted, this component behaves
  // exactly as before (hidden entirely for a single page).
  total?: number;
}

export function Pagination({ page, totalPages, onChange, total }: PaginationProps) {
  const showPager = totalPages > 1;
  if (!showPager && total === undefined) {
    return null;
  }
  return (
    <div className="sakar-pagination">
      {total !== undefined && (
        <span className="sakar-pagination-total">
          Total {total}
        </span>
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
