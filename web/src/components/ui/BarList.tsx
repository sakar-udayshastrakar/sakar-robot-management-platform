export interface BarListItem {
  key: string;
  label: string;
  value: number;
}

// A plain, dependency-free horizontal bar chart — this app has no charting
// library, and adding one for a single feature wasn't worth the weight.
export function BarList({ items, emptyLabel = 'No Data' }: { items: BarListItem[]; emptyLabel?: string }) {
  if (items.length === 0) {
    return <div className="sakar-table-empty"><span className="sakar-table-empty-title">{emptyLabel}</span></div>;
  }
  const max = Math.max(...items.map((i) => i.value), 1);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {items.map((item) => (
        <div key={item.key} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ width: 160, flexShrink: 0, fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={item.label}>
            {item.label}
          </span>
          <div style={{ flex: 1, background: 'var(--sakar-bg)', borderRadius: 4, height: 20 }}>
            <div
              style={{
                width: `${(item.value / max) * 100}%`,
                background: 'var(--sakar-primary, #2563eb)',
                height: '100%',
                borderRadius: 4,
                minWidth: item.value > 0 ? 4 : 0,
              }}
            />
          </div>
          <span style={{ width: 48, textAlign: 'right', fontSize: 13, fontWeight: 600 }}>{item.value}</span>
        </div>
      ))}
    </div>
  );
}
