import { useNavigate } from 'react-router-dom';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Icon } from '../../components/ui/Icon';

interface DashboardCard {
  title: string;
  description: string;
  path: string;
  icon: keyof typeof Icon;
}

const CARDS: DashboardCard[] = [
  { title: 'Operation Ranking', description: 'Ranking of operating mileage and number of tasks by store and robot.', path: '/operational-dashboard/operation-ranking', icon: 'barChart' },
  { title: 'Store Real-Time Data Statistics', description: 'Display the number of tasks and mode proportions in real time.', path: '/operational-dashboard/store-realtime', icon: 'activity' },
  { title: 'Use Retention Analytics', description: 'Store machine usage retention analysis.', path: '/operational-dashboard/retention', icon: 'timeline' },
  { title: 'Hotel Task Record', description: 'Present an overview of the historical tasks of the fleet.', path: '/operational-dashboard/hotel-task-record', icon: 'clipboard' },
];

// Operational Dashboard → Home page. Real navigation into the four
// sub-pages below — see each page's own comment for which of its figures
// are computed from real data and which are honestly "Not tracked".
export function OperationalDashboardHomePage() {
  const navigate = useNavigate();

  return (
    <div>
      <Breadcrumb items={[{ label: 'Home page' }, { label: 'Home page' }]} />
      <PageHeader title="Operational Dashboard" />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 16 }}>
        {CARDS.map((card) => {
          const IconComponent = Icon[card.icon];
          return (
            <button
              key={card.path}
              type="button"
              className="sakar-card"
              style={{ textAlign: 'left', cursor: 'pointer', border: 'none', padding: 20 }}
              onClick={() => navigate(card.path)}
            >
              <IconComponent style={{ width: 32, height: 32, color: 'var(--sakar-primary)' }} />
              <h3 style={{ margin: '12px 0 6px', fontSize: 15 }}>{card.title}</h3>
              <p style={{ margin: 0, fontSize: 13, color: 'var(--sakar-text-faint)' }}>{card.description}</p>
            </button>
          );
        })}
      </div>
    </div>
  );
}
