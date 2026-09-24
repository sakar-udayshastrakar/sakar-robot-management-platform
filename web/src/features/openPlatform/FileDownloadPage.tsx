import { API_ORIGIN } from '../../api/client';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { PageHeader } from '../../components/ui/PageHeader';
import { Card } from '../../components/ui/Card';
import { DataTable } from '../../components/ui/DataTable';

interface ApiDocument {
  fileName: string;
  language: string;
  version: string;
  href: string;
}

// Open Platform → File download. Points at this backend's own real, already
// -live OpenAPI spec (springdoc's /swagger-ui.html + /api-docs), never a
// fabricated multi-version document history — this backend has exactly one
// live API surface, versioned by its own OpenApiConfig ("phase-1"), not five
// invented historical releases.
const DOCUMENTS: ApiDocument[] = [
  { fileName: 'Sakar Cloud Backend API Reference', language: 'English version', version: 'phase-1', href: `${API_ORIGIN}/swagger-ui.html` },
];

export function FileDownloadPage() {
  return (
    <div>
      <Breadcrumb items={[{ label: 'Open Platform' }, { label: 'File download' }]} />
      <PageHeader title="File download" subtitle="This backend's own live API reference — not a static document archive." />

      <Card title={`Documents (${DOCUMENTS.length})`}>
        <DataTable
          rows={DOCUMENTS}
          rowKey={(d) => d.fileName}
          emptyTitle="No Data"
          columns={[
            { key: 'fileName', header: 'File name', render: (d: ApiDocument) => d.fileName },
            { key: 'language', header: 'Language selection', render: (d: ApiDocument) => d.language },
            { key: 'version', header: 'File version', render: (d: ApiDocument) => d.version },
            {
              key: 'action',
              header: 'Operate',
              align: 'right' as const,
              render: (d: ApiDocument) => (
                <a href={d.href} target="_blank" rel="noreferrer" className="sakar-link-btn">Preview</a>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}
