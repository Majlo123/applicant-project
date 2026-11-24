'use client';
import { useEffect, useRef } from 'react';
import type { Person } from '@/lib/personApi';

export function DeleteConfirmation({ person, busy, error, onCancel, onConfirm }: { person: Person; busy: boolean; error: string; onCancel: () => void; onConfirm: () => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { const dialog = ref.current; dialog?.showModal(); return () => dialog?.close(); }, []);
  return <dialog ref={ref} aria-labelledby="delete-title" aria-describedby="delete-description" onCancel={event => { event.preventDefault(); if (!busy) onCancel(); }}>
    <div className="dialog-icon" aria-hidden="true">!</div><h2 id="delete-title">Delete this person?</h2><p id="delete-description">The record for <strong>{person.firstName} {person.lastName}</strong> will be permanently deleted. This action cannot be undone.</p>
    {error && <p className="notice error" role="alert">{error}</p>}<div className="form-actions"><button autoFocus className="secondary" disabled={busy} onClick={onCancel}>Cancel</button><button className="danger" disabled={busy} onClick={onConfirm}>{busy ? 'Deleting…' : 'Delete person'}</button></div>
  </dialog>;
}
