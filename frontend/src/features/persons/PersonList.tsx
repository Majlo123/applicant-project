'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { personApi, type Person } from '@/lib/personApi';
import { DeleteConfirmation } from './DeleteConfirmation';

export function PersonList() {
  const [people, setPeople] = useState<Person[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [selected, setSelected] = useState<Person | null>(null);
  const [busy, setBusy] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    personApi.list().then(result => { if (active) setPeople([...result].sort((a, b) => a.id - b.id)); })
      .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : 'Unable to load people.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [attempt]);
  async function remove() {
    if (!selected || busy) return;
    setBusy(true); setDeleteError('');
    try {
      await personApi.remove(selected.id);
      setPeople(current => current.filter(person => person.id !== selected.id));
      setSelected(null); setNotice('Person deleted successfully.');
    } catch (reason) { setDeleteError(reason instanceof Error ? reason.message : 'Unable to delete this person.'); }
    finally { setBusy(false); }
  }
  return <><div className="page-heading heading-row"><div><p className="eyebrow">YOUR WORKSPACE</p><h1>People</h1><p>Keep personal information organized, in one place.</p></div><Link className="button" href="/persons/new"><span aria-hidden="true">＋</span> Add person</Link></div>
    {notice && <div className="notice success" role="status">{notice}</div>}
    <section className="panel list-panel" aria-labelledby="directory-title"><div className="panel-heading heading-row"><div><h2 id="directory-title">Person directory <span className="count">{loading || error ? '—' : people.length}</span></h2><p>View and manage your person records.</p></div><span className="table-caption">Ordered by ID</span></div>
      {loading ? <div className="empty" role="status">Loading people…</div> : error ? <div className="empty"><p className="notice error" role="alert">{error}</p><button className="secondary" onClick={() => { setLoading(true); setError(''); setAttempt(value => value + 1); }}>Try again</button></div> : !people.length ? <div className="empty"><span className="empty-icon" aria-hidden="true">◎</span><h2>No people yet</h2><p>Add your first person to get started.</p><Link className="button" href="/persons/new">Add person</Link></div> :
      <div className="table-scroll"><table><thead><tr><th scope="col">Person</th><th scope="col">Email address</th><th scope="col">Date of birth</th><th scope="col" className="right">Actions</th></tr></thead><tbody>{people.map(person => <tr key={person.id}><td><div className="person-cell"><span className="avatar" aria-hidden="true">{person.firstName.charAt(0)}{person.lastName.charAt(0)}</span><div><Link className="person-name" href={`/persons/${person.id}`}>{person.firstName} {person.lastName}</Link><span className="record-id">ID {String(person.id).padStart(3, '0')}</span></div></div></td><td>{person.email}</td><td><time dateTime={person.dateOfBirth}>{person.dateOfBirth}</time></td><td><div className="row-actions"><Link href={`/persons/${person.id}`} aria-label={`View ${person.firstName} ${person.lastName}`}>View</Link><Link href={`/persons/${person.id}/edit`} aria-label={`Edit ${person.firstName} ${person.lastName}`}>Edit</Link><button className="text-danger" onClick={() => { setDeleteError(''); setSelected(person); }} aria-label={`Delete ${person.firstName} ${person.lastName}`}>Delete</button></div></td></tr>)}</tbody></table></div>}
      {!loading && !error && people.length > 0 && <div className="table-footer">{people.length} {people.length === 1 ? 'person' : 'people'} in your directory</div>}
    </section>{selected && <DeleteConfirmation person={selected} busy={busy} error={deleteError} onCancel={() => setSelected(null)} onConfirm={remove} />}</>;
}
