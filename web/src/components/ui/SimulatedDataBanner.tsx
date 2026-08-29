export function SimulatedDataBanner({ label = 'Simulated / test data' }: { label?: string }) {
  return (
    <div className="sakar-banner sakar-banner--simulated" role="note">
      <span aria-hidden="true">&#9888;&#65039;</span>
      <span>
        <strong>{label}.</strong> No backend REST endpoint exists for this data yet — these rows are
        generated in the browser for UI development only and are never mixed with real API data. See the
        Phase 4 implementation report for the tracked gap.
      </span>
    </div>
  );
}

export function UnavailableFeature({ reason }: { reason: string }) {
  return (
    <div className="sakar-banner sakar-banner--unavailable" role="note">
      <span aria-hidden="true">&#128274;</span>
      <span>{reason}</span>
    </div>
  );
}
