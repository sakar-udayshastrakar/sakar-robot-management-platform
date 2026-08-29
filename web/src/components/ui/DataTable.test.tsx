import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DataTable } from './DataTable';

interface Row {
  id: string;
  name: string;
}

describe('DataTable', () => {
  it('renders an empty state when there are no rows', () => {
    render(
      <DataTable<Row>
        rows={[]}
        rowKey={(r) => r.id}
        emptyTitle="No robots"
        columns={[{ key: 'name', header: 'Name', render: (r) => r.name }]}
      />,
    );
    expect(screen.getByText('No robots')).toBeInTheDocument();
  });

  it('renders one row per item with the configured columns', () => {
    render(
      <DataTable<Row>
        rows={[{ id: '1', name: 'CleanBot Alpha' }, { id: '2', name: 'CleanBot Beta' }]}
        rowKey={(r) => r.id}
        columns={[{ key: 'name', header: 'Name', render: (r) => r.name }]}
      />,
    );
    expect(screen.getByText('CleanBot Alpha')).toBeInTheDocument();
    expect(screen.getByText('CleanBot Beta')).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(3); // header + 2 data rows
  });
});
