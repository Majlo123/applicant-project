// One-time "saved" notice handed from the form to the detail page after the server confirmed the save.
// It lives only in memory, so refreshing the page or typing the URL never shows a success message.
export type SavedAction = 'created' | 'updated';

let pending: { id: number; action: SavedAction } | null = null;

export function rememberSaved(id: number, action: SavedAction) {
  pending = { id, action };
}

export function peekSaved(id: string): SavedAction | null {
  return pending && String(pending.id) === id ? pending.action : null;
}

export function clearSaved(id: string) {
  if (pending && String(pending.id) === id) pending = null;
}
