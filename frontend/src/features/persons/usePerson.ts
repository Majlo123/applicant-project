'use client';
import { useEffect, useState } from 'react';
import { personApi, type Person } from '@/lib/personApi';

export function usePerson(id: string) {
  const [person, setPerson] = useState<Person | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    personApi.get(id).then(value => { if (active) setPerson(value); })
      .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : 'Unable to load this person.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, attempt]);
  return { person, loading, error, retry: () => { setLoading(true); setError(''); setAttempt(value => value + 1); } };
}
