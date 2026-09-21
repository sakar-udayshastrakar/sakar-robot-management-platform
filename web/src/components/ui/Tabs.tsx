export interface TabItem<K extends string = string> {
  key: K;
  label: string;
}

interface TabsProps<K extends string = string> {
  tabs: TabItem<K>[];
  active: K;
  onChange: (key: K) => void;
  ariaLabel?: string;
}

// Shared tab strip. The styles previously lived in features/robots/robots.css
// and are now in components.css, so this renders identically to the existing
// hand-rolled strip on Robot Detail — that page keeps its own markup until the
// Robot Detail phase migrates it.
export function Tabs<K extends string = string>({ tabs, active, onChange, ariaLabel }: TabsProps<K>) {
  return (
    <div className="sakar-tabs sakar-scroll-x" role="tablist" aria-label={ariaLabel}>
      {tabs.map((t) => (
        <button
          key={t.key}
          type="button"
          role="tab"
          aria-selected={active === t.key}
          className={'sakar-tab' + (active === t.key ? ' sakar-tab--active' : '')}
          onClick={() => onChange(t.key)}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
