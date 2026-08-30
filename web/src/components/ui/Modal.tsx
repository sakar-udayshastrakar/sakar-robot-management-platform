import { useEffect, useRef, type ReactNode } from 'react';
import { Icon } from './Icon';

interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}

export function Modal({ open, title, onClose, children, footer }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    dialogRef.current?.focus();
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
    <div className="sakar-overlay" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={dialogRef}
        className="sakar-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="sakar-modal-title"
        tabIndex={-1}
      >
        <div className="sakar-modal-header">
          <h2 className="sakar-modal-title" id="sakar-modal-title">{title}</h2>
          <button type="button" className="sakar-modal-close" onClick={onClose} aria-label="Close dialog">
            <Icon.x width={18} height={18} />
          </button>
        </div>
        <div className="sakar-modal-body">{children}</div>
        {footer && <div className="sakar-modal-footer">{footer}</div>}
      </div>
    </div>
  );
}
