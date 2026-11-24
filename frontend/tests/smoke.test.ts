import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient, ApiError } from '../src/lib/apiClient';
import { normalizePerson, validatePerson } from '../src/features/persons/personValidation';

const person = { firstName: 'Anna', lastName: 'Novak', email: 'anna.novak@example.com', dateOfBirth: '1994-05-12' };
afterEach(() => vi.unstubAllGlobals());
describe('Person basics', () => {
  it('accepts an ordinary complete record', () => expect(validatePerson(person, '2026-06-15')).toEqual({}));
  it('requires all four fields', () => expect(Object.keys(validatePerson({ firstName: '', lastName: ' ', email: '', dateOfBirth: '' }))).toHaveLength(4));
  it('trims surrounding whitespace and preserves email spelling', () => expect(normalizePerson({ ...person, firstName: ' Anna ', email: ' Anna@example.com ' })).toEqual({ ...person, firstName: 'Anna', email: 'Anna@example.com' }));
  it('reads a person from the API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 1, ...person }))));
    await expect(apiClient('/api/persons/1')).resolves.toEqual({ id: 1, ...person });
  });
  it('retains useful server field feedback', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'VALIDATION_FAILED', message: 'Please check the entered values', fieldErrors: { email: 'Enter a valid email address' } }), { status: 400 })));
    await expect(apiClient('/api/persons', { method: 'POST' })).rejects.toMatchObject({ code: 'VALIDATION_FAILED', fieldErrors: { email: 'Enter a valid email address' } });
  });
  it('reports an unavailable connection', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(apiClient('/api/persons')).rejects.toBeInstanceOf(ApiError);
  });
});
