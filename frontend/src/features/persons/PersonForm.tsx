'use client';
import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/apiClient';
import { personApi, type FieldErrors, type PersonInput } from '@/lib/personApi';
import { emptyPerson, normalizePerson, validatePerson } from './personValidation';
import { usePerson } from './usePerson';

type FieldsProps = { value: PersonInput; errors: FieldErrors; busy: boolean; onChange: (value: PersonInput) => void };
function Fields({ value, errors, busy, onChange }: FieldsProps) {
  return <fieldset disabled={busy}><div className="form-grid">{([
    ['firstName', 'First name', 'text', 'given-name'], ['lastName', 'Last name', 'text', 'family-name'],
    ['email', 'Email address', 'email', 'email'], ['dateOfBirth', 'Date of birth', 'date', 'bday'],
  ] as const).map(([key, label, type, autoComplete]) => <div className={`field ${key === 'email' ? 'wide' : ''}`} key={key}>
    <label htmlFor={key}>{label} <span aria-hidden="true">*</span></label>
    <input id={key} name={key} type={type} value={value[key]} autoComplete={autoComplete} required
      maxLength={key === 'email' ? 254 : key === 'dateOfBirth' ? undefined : 100}
      aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `${key}-error` : key === 'dateOfBirth' ? 'date-hint' : undefined}
      onChange={event => onChange({ ...value, [key]: event.target.value })} />
    {errors[key] && <p className="field-error" id={`${key}-error`}>{errors[key]}</p>}
    {key === 'dateOfBirth' && <p className="hint" id="date-hint">Use a calendar date, up to and including today.</p>}
  </div>)}</div></fieldset>;
}

export function CreatePersonForm() {
  const router = useRouter();
  const [value, setValue] = useState<PersonInput>(emptyPerson);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    const validation = validatePerson(value);
    setErrors(validation); setError('');
    if (Object.keys(validation).length) { setError('Please check the entered values.'); return; }
    setBusy(true);
    try {
      const person = await personApi.create(normalizePerson(value));
      router.push(`/persons/${person.id}?saved=created`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to save. Please try again.');
      if (reason instanceof ApiError) setErrors(reason.fieldErrors);
    } finally { setBusy(false); }
  }
  return <FormShell title="Add a person" subtitle="Create a new record with the information below." cancel="/persons" value={value} onChange={setValue} errors={errors} error={error} busy={busy} onSubmit={submit} />;
}

export function EditPersonForm({ id }: { id: string }) {
  const { person, loading, error, retry } = usePerson(id);
  if (loading) return <div className="panel loading" role="status">Loading person…</div>;
  if (error || !person) return <div className="panel"><p role="alert">{error || 'Person not found.'}</p><button onClick={retry}>Try again</button> <Link href="/persons">Back to people</Link></div>;
  return <EditLoaded key={person.id} id={id} initial={person} />;
}

function EditLoaded({ id, initial }: { id: string; initial: PersonInput }) {
  const router = useRouter();
  const [value, setValue] = useState<PersonInput>(initial);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    const validation = validatePerson(value);
    setErrors(validation); setError('');
    if (Object.keys(validation).length) { setError('Please check the entered values.'); return; }
    setBusy(true);
    try {
      const person = await personApi.update(id, normalizePerson(value));
      router.push(`/persons/${person.id}?saved=updated`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to save. Please try again.');
      if (reason instanceof ApiError) setErrors(reason.fieldErrors);
    } finally { setBusy(false); }
  }
  return <FormShell title="Edit person" subtitle="Update the information for this person." cancel={`/persons/${id}`} value={value} onChange={setValue} errors={errors} error={error} busy={busy} onSubmit={submit} />;
}

function FormShell(props: FieldsProps & { title: string; subtitle: string; cancel: string; error: string; onSubmit: (event: FormEvent) => void }) {
  return <div className="narrow"><Link className="back" href="/persons">← All people</Link><div className="page-heading"><p className="eyebrow">PERSON RECORD</p><h1>{props.title}</h1><p>{props.subtitle}</p></div>
    <form className="panel form-panel" noValidate onSubmit={props.onSubmit} aria-busy={props.busy}><div className="panel-heading"><h2>Personal information</h2><p>All fields are required.</p></div>
      {props.error && <div className="notice error" role="alert">{props.error}</div>}<Fields {...props} />
      <div className="form-actions">{props.busy ? <span className="muted">Please wait…</span> : <Link className="button secondary" href={props.cancel}>Cancel</Link>}<button disabled={props.busy} type="submit">{props.busy ? 'Saving…' : 'Save person'}</button></div>
    </form></div>;
}
