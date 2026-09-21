import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataTable } from './DataTable';

interface Row {
  id: string;
  name: string;
}

const ROWS: Row[] = [
  { id: '1', name: 'CleanBot Alpha' },
  { id: '2', name: 'CleanBot Beta' },
];

const NAME_COL = [{ key: 'name', header: 'Name', render: (r: Row) => r.name }];

describe('DataTable', () => {
  it('renders an empty state when there are no rows', () => {
    render(<DataTable<Row> rows={[]} rowKey={(r) => r.id} emptyTitle="No robots" columns={NAME_COL} />);
    expect(screen.getByText('No robots')).toBeInTheDocument();
  });

  it('renders one row per item with the configured columns', () => {
    render(<DataTable<Row> rows={ROWS} rowKey={(r) => r.id} columns={NAME_COL} />);
    expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument();
    expect(screen.getByText('CleanBot Beta')).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(3); // header + 2 data rows
  });

  it('adds no extra columns unless the optional features are enabled', () => {
    render(<DataTable<Row> rows={ROWS} rowKey={(r) => r.id} columns={NAME_COL} />);
    expect(screen.getAllByRole('columnheader')).toHaveLength(1);
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('numbers rows from the given offset when indexColumn is enabled', () => {
    render(
      <DataTable<Row> rows={ROWS} rowKey={(r) => r.id} columns={NAME_COL} indexColumn indexOffset={20} />,
    );
    expect(screen.getByText('No.')).toBeInTheDocument();
    expect(screen.getByText('21')).toBeInTheDocument();
    expect(screen.getByText('22')).toBeInTheDocument();
  });

  it('shows skeleton rows instead of data while loading', () => {
    const { container } = render(
      <DataTable<Row> rows={[]} rowKey={(r) => r.id} columns={NAME_COL} loading skeletonRows={3} />,
    );
    // Loading wins over the empty state, so the table shape is preserved.
    expect(screen.queryByText('No records')).not.toBeInTheDocument();
    expect(container.querySelectorAll('.sakar-skeleton')).toHaveLength(3);
  });

  it('renders an error surface with a working retry instead of the table', async () => {
    const onRetry = vi.fn();
    render(
      <DataTable<Row>
        rows={ROWS}
        rowKey={(r) => r.id}
        columns={NAME_COL}
        error="Upstream timed out"
        onRetry={onRetry}
      />,
    );
    expect(screen.getByText('Upstream timed out')).toBeInTheDocument();
    expect(screen.queryByText('CleanBot Alpha')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('reports a sort intent and toggles direction on the active column', async () => {
    const onSort = vi.fn();
    const cols = [{ key: 'name', header: 'Name', render: (r: Row) => r.name, sortable: true }];
    const { rerender } = render(
      <DataTable<Row> rows={ROWS} rowKey={(r) => r.id} columns={cols} onSort={onSort} />,
    );
    await userEvent.click(screen.getByRole('columnheader', { name: /Name/ }));
    expect(onSort).toHaveBeenLastCalledWith('name', 'asc');

    rerender(
      <DataTable<Row>
        rows={ROWS}
        rowKey={(r) => r.id}
        columns={cols}
        onSort={onSort}
        sortKey="name"
        sortDirection="asc"
      />,
    );
    expect(screen.getByRole('columnheader', { name: /Name/ })).toHaveAttribute('aria-sort', 'ascending');
    await userEvent.click(screen.getByRole('columnheader', { name: /Name/ }));
    expect(onSort).toHaveBeenLastCalledWith('name', 'desc');
  });

  it('does not make headers sortable without an onSort handler', () => {
    const cols = [{ key: 'name', header: 'Name', render: (r: Row) => r.name, sortable: true }];
    render(<DataTable<Row> rows={ROWS} rowKey={(r) => r.id} columns={cols} />);
    expect(screen.getByRole('columnheader', { name: 'Name' })).not.toHaveAttribute('aria-sort');
  });

  it('expands and collapses a row on demand', async () => {
    render(
      <DataTable<Row>
        rows={ROWS}
        rowKey={(r) => r.id}
        columns={NAME_COL}
        renderExpanded={(r) => <span>Detail for {r.name}</span>}
      />,
    );
    expect(screen.queryByText(/Detail for CleanBot Alpha/)).not.toBeInTheDocument();

    const [firstExpander] = screen.getAllByRole('button', { name: 'Expand row' });
    await userEvent.click(firstExpander);
    expect(screen.getByText(/Detail for CleanBot Alpha/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Collapse row' }));
    expect(screen.queryByText(/Detail for CleanBot Alpha/)).not.toBeInTheDocument();
  });

  it('supports controlled row selection including select-all', async () => {
    const onSelectionChange = vi.fn();
    render(
      <DataTable<Row>
        rows={ROWS}
        rowKey={(r) => r.id}
        columns={NAME_COL}
        selectable
        selectedKeys={[]}
        onSelectionChange={onSelectionChange}
      />,
    );
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select row 1' }));
    expect(onSelectionChange).toHaveBeenLastCalledWith(['1']);

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select all rows' }));
    expect(onSelectionChange).toHaveBeenLastCalledWith(['1', '2']);
  });

  it('clears the selection when select-all is toggled off', async () => {
    const onSelectionChange = vi.fn();
    render(
      <DataTable<Row>
        rows={ROWS}
        rowKey={(r) => r.id}
        columns={NAME_COL}
        selectable
        selectedKeys={['1', '2']}
        onSelectionChange={onSelectionChange}
      />,
    );
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select all rows' }));
    expect(onSelectionChange).toHaveBeenLastCalledWith([]);
  });
});
