import { useEffect, useRef, type ReactNode } from 'react';
import { Icon } from './Icon';

interface DrawerProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}

export function Drawer({ open, title, onClose, children }: DrawerProps) {
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    panelRef.current?.focus();
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        onClose();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  return (
    <div className="sakar-overlay sakar-overlay--drawer" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={panelRef}
        className="sakar-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="sakar-drawer-title"
        tabIndex={-1}
      >
        <div className="sakar-modal-header">
          <h2 className="sakar-modal-title" id="sakar-drawer-title">{title}</h2>
          <button type="button" className="sakar-modal-close" onClick={onClose} aria-label="Close panel">
            <Icon.x width={18} height={18} />
          </button>
        </div>
        <div className="sakar-modal-body" style={{ flex: 1, overflowY: 'auto' }}>{children}</div>
      </div>
    </div>
  );
}
