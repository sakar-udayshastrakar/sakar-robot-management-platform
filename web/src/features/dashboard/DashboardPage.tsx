import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getHotelTaskRecord } from '../../api/dashboard';
import { useApi } from '../../hooks/useApi';
import { useRecentlyUsed } from '../../hooks/useRecentlyUsed';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { Carousel } from '../../components/ui/Carousel';
import { Tabs } from '../../components/ui/Tabs';
import { BarList } from '../../components/ui/BarList';
import { UnavailableFeature } from '../../components/ui/SimulatedDataBanner';

// Sakar's own brand orange (--sakar-primary-600/700/800 in index.css) —
// deliberately not the reference product's blue banner, to keep this
// carousel visually consistent with the rest of the app. Same gradient on
// every slide, so the carousel reads as one consistent brand banner rather
// than a different color per slide.
const HERO_GRADIENT = 'linear-gradient(120deg, #ff9d61, #ff6100)';

const HERO_SLIDES = [
  {
    key: 'welcome',
    title: 'SAKAR ROBOT MANAGEMENT PLATFORM',
    subtitle: 'One console for fleet operations, IoT devices, and the Open Platform API.',
    path: '/robots',
  },
  {
    key: 'iot',
    title: 'IoT PLATFORM',
    subtitle: 'Elevator devices, cloud ladder control, and phone module device management.',
    path: '/iot/elevator-management',
  },
  {
    key: 'open-platform',
    title: 'OPEN PLATFORM',
    subtitle: 'Register your organization and manage API applications for third-party integration.',
    path: '/open-platform/customer-registration',
  },
];

function HeroCarousel() {
  const navigate = useNavigate();
  return (
    <Carousel
      ariaLabel="Dashboard highlights"
      autoplayMs={6000}
      slides={HERO_SLIDES.map((s) => ({
        key: s.key,
        content: (
          <div
            role="button"
            tabIndex={0}
            onClick={() => navigate(s.path)}
            onKeyDown={(e) => { if (e.key === 'Enter') navigate(s.path); }}
            style={{
              background: HERO_GRADIENT, minHeight: 180, display: 'flex', alignItems: 'center',
              padding: '0 40px', color: '#fff', cursor: 'pointer',
            }}
          >
            <div>
              <h2 style={{ margin: 0, fontSize: 26, letterSpacing: 0.5 }}>{s.title}</h2>
              <p style={{ margin: '10px 0 0', fontSize: 14, opacity: 0.9, maxWidth: 480 }}>{s.subtitle}</p>
            </div>
          </div>
        ),
      }))}
    />
  );
}

function RecentlyUsedCard() {
  const navigate = useNavigate();
  const items = useRecentlyUsed();
  return (
    <Card title="Recently Used">
      {items.length === 0 ? (
        <p className="sakar-page-subtitle">Pages you visit will show up here.</p>
      ) : (
        <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
          {items.map((item) => (
            <button
              key={item.path}
              type="button"
              onClick={() => item.path && navigate(item.path)}
              style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, background: 'none', border: 'none', cursor: 'pointer', width: 90 }}
            >
              <span style={{ width: 44, height: 44, borderRadius: 10, background: 'var(--sakar-primary-soft, var(--sakar-bg))', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--sakar-primary)' }}>
                <item.icon width={20} height={20} />
              </span>
              <span style={{ fontSize: 12, textAlign: 'center' }}>{item.label}</span>
            </button>
          ))}
        </div>
      )}
    </Card>
  );
}

function TaskDataDetailsCard() {
  const [tab, setTab] = useState<'task' | 'mileage'>('task');
  const range = useMemo(() => {
    const to = new Date();
    const from = new Date(to.getTime() - 6 * 24 * 60 * 60 * 1000);
    return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
  }, []);
  const { data, status } = useApi(() => getHotelTaskRecord(range), [range]);

  return (
    <>
      <Card title="Seven-day Overview" actions={<span className="sakar-page-subtitle">{new Date().toLocaleString()} Updated</span>}>
        {status === 'success' && data ? (
          <p style={{ fontSize: 28, fontWeight: 700, margin: 0 }}>
            {data.totalVolumeOfTask} <span style={{ fontSize: 13, fontWeight: 400, color: 'var(--sakar-text-faint)' }}>tasks in the last 7 days</span>
          </p>
        ) : (
          <p className="sakar-page-subtitle">No data available</p>
        )}
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16, marginTop: 20 }}>
        <Card title="Task Distribution">
          {data?.taskTypeBreakdown && data.taskTypeBreakdown.length > 0 ? (
            <BarList items={data.taskTypeBreakdown.map((s) => ({ key: s.taskType, label: s.taskType, value: s.count }))} />
          ) : (
            <p className="sakar-page-subtitle">No data available</p>
          )}
        </Card>

        <Card
          title="Task Statistics"
          actions={<Tabs ariaLabel="Task statistics metric" tabs={[{ key: 'task', label: 'Task' }, { key: 'mileage', label: 'Mileage' }]} active={tab} onChange={setTab} />}
        >
          <p className="sakar-page-subtitle" style={{ marginBottom: 8 }}>Unit: Times</p>
          {tab === 'task' ? (
            data?.dailyBreakdown && data.dailyBreakdown.length > 0
              ? <BarList items={data.dailyBreakdown.map((d) => ({ key: d.date, label: d.date, value: d.count }))} />
              : <p className="sakar-page-subtitle">No data available</p>
          ) : (
            <UnavailableFeature reason="No distance/odometer concept exists anywhere in this platform — never fabricated as zero." />
          )}
        </Card>
      </div>
    </>
  );
}

export function DashboardPage() {
  return (
    <div>
      <PageHeader title="Dashboard" />

      <div style={{ marginBottom: 20 }}>
        <HeroCarousel />
      </div>

      <div style={{ marginBottom: 20 }}>
        <RecentlyUsedCard />
      </div>

      <TaskDataDetailsCard />
    </div>
  );
}
