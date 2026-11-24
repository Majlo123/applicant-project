'use client';
import { useEffect, useState } from 'react';
import { ApiError } from '@/lib/apiClient';
import { personApi, type Person } from '@/lib/personApi';

export function usePerson(id: string) {
  const [person, setPerson] = useState<Person | null>(null);
  const [error, setError] = useState('');
  const [retryable, setRetryable] = useState(true);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    personApi.get(id).then(value => { if (active) setPerson(value); })
      .catch(reason => {
        if (!active) return;
        setError(reason instanceof Error ? reason.message : 'Unable to load this person.');
        // A missing person (404) or an invalid ID (400) will not change by trying again.
        setRetryable(!(reason instanceof ApiError && (reason.status === 404 || reason.status === 400)));
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, attempt]);
  return { person, loading, error, retryable, retry: () => { setLoading(true); setError(''); setAttempt(value => value + 1); } };
}
