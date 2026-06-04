interface RefreshButtonProps {
  onClick: () => void
  disabled?: boolean
}

export function RefreshButton({ onClick, disabled }: RefreshButtonProps) {
  return (
    <button
      type="button"
      className="btn btn-sm btn-refresh"
      onClick={onClick}
      disabled={disabled}
      aria-label="Refresh"
      title="Refresh"
    >
      <svg
        className="refresh-icon"
        viewBox="0 0 24 24"
        width="16"
        height="16"
        aria-hidden="true"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M21 12a9 9 0 1 1-2.64-6.36" />
        <path d="M21 3v6h-6" />
      </svg>
    </button>
  )
}
