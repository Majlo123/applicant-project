'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { usePerson } from './usePerson';

export function PersonDetail({ id }: { id: string }) {
  const { person, loading, error, retry } = usePerson(id);
  const params = useSearchParams();
  const [saved] = useState(() => params.get('saved'));
  if (loading) return <div className="panel loading" role="status">Loading person…</div>;
  if (error || !person) return <div className="panel"><p role="alert">{error || 'Person not found.'}</p><button onClick={retry}>Try again</button> <Link href="/persons">Back to people</Link></div>;
  return <div className="narrow"><Link className="back" href="/persons">← All people</Link><div className="page-heading heading-row"><div><p className="eyebrow">PERSON RECORD · {String(person.id).padStart(3, '0')}</p><h1>{person.firstName} {person.lastName}</h1><p>Personal information</p></div><Link className="button secondary" href={`/persons/${id}/edit`}>Edit person</Link></div>
    {(saved === 'created' || saved === 'updated') && <div className="notice success" role="status">Person {saved} successfully.</div>}<section className="panel"><div className="panel-heading"><h2>Record details</h2><p>Information saved for this person.</p></div><dl className="details">{([['First name', person.firstName], ['Last name', person.lastName], ['Email address', person.email], ['Date of birth', person.dateOfBirth]]).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl></section></div>;
}
