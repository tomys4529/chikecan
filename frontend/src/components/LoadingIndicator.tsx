export function LoadingIndicator({ label = '読み込み中...' }: { label?: string }) {
  return (
    <p role="status" className="loading-indicator">
      <span className="loading-indicator__spinner" aria-hidden="true" />
      {label}
    </p>
  );
}
