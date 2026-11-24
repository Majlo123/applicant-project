import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient, ApiError } from '../src/lib/apiClient';
import { personApi } from '../src/lib/personApi';
import { characterCount, normalizePerson, validatePerson } from '../src/features/persons/personValidation';
import { clearSaved, peekSaved, rememberSaved } from '../src/features/persons/savedNotice';

const person = { firstName: 'Anna', lastName: 'Novak', email: 'anna.novak@example.com', dateOfBirth: '1994-05-12' };
const today = '2026-06-15';
const cp = (...codePoints: number[]) => String.fromCodePoint(...codePoints);
const rareLetter = cp(0x20000); // one character, two UTF-16 code units
afterEach(() => vi.unstubAllGlobals());

describe('Names', () => {
  it.each(['Zoë', "O'Brien", 'D’Angelo', 'Anne-Marie', 'José María', 'Đorđe', 'Łukasz', 'Nguyễn', '李小龍', 'Zoe' + cp(0x0308)])('accepts %s', name =>
    expect(validatePerson({ ...person, firstName: name, lastName: name }, today)).toEqual({}));
  it.each(['Edit1', 'Anna!', '<b>Anna</b>', 'Anna_Novak', 'Anna.Maria'])('rejects %s', name =>
    expect(validatePerson({ ...person, firstName: name }, today)).toEqual({ firstName: 'Use letters, spaces, apostrophes and hyphens only' }));
  it('counts length in characters, not UTF-16 units', () => {
    expect(characterCount(rareLetter.repeat(100))).toBe(100);
    expect(validatePerson({ ...person, firstName: rareLetter.repeat(100) }, today)).toEqual({});
    expect(validatePerson({ ...person, firstName: rareLetter.repeat(101) }, today)).toEqual({ firstName: 'Use 100 characters or fewer' });
  });
});

describe('Email', () => {
  it('allows 254 characters and rejects 255 with a length message', () => {
    expect(validatePerson({ ...person, email: rareLetter.repeat(242) + '@example.com' }, today)).toEqual({});
    expect(validatePerson({ ...person, email: rareLetter.repeat(243) + '@example.com' }, today)).toEqual({ email: 'Use 254 characters or fewer' });
  });
  it('rejects Unicode whitespace inside the address', () => {
    for (const email of [`anna${cp(0xa0)}novak@example.com`, `anna@exam${cp(0x2003)}ple.com`])
      expect(validatePerson({ ...person, email }, today)).toEqual({ email: 'Enter a valid email address' });
  });
  it('keeps international domains as typed', () => expect(normalizePerson({ ...person, email: ' anna@bücher.example ' }).email).toBe('anna@bücher.example'));
});

describe('Whitespace', () => {
  it('trims Unicode whitespace around text fields', () => {
    const padded = { ...person, firstName: `${cp(0xa0)}Zoë${cp(0x3000)}`, email: `${cp(0x2003)} Anna@Example.com\t` };
    expect(normalizePerson(padded)).toEqual({ ...person, firstName: 'Zoë', email: 'Anna@Example.com' });
  });
  it('treats whitespace-only values as missing', () => {
    const blank = cp(0xa0, 0x3000);
    expect(validatePerson({ firstName: blank, lastName: blank, email: blank, dateOfBirth: '' }, today)).toEqual({
      firstName: 'Enter a first name', lastName: 'Enter a last name', email: 'Enter an email address', dateOfBirth: 'Enter a date of birth' });
  });
});

describe('Date of birth', () => {
  it('accepts today and rejects tomorrow', () => {
    expect(validatePerson({ ...person, dateOfBirth: today }, today)).toEqual({});
    expect(validatePerson({ ...person, dateOfBirth: '2026-06-16' }, today)).toEqual({ dateOfBirth: 'Date of birth cannot be in the future' });
  });
  it.each(['2023-02-29', '0000-02-29', '1994-5-12'])('rejects %s as not a calendar date', dateOfBirth =>
    expect(validatePerson({ ...person, dateOfBirth }, today)).toEqual({ dateOfBirth: 'Enter a valid calendar date' }));
  it('explains an impossible date typed into the date field', () =>
    expect(validatePerson({ ...person, dateOfBirth: '' }, today, true)).toEqual({ dateOfBirth: 'Enter a valid calendar date' }));
});

describe('API client', () => {
  it('treats 204 No Content from delete as success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(personApi.remove(1)).resolves.toBeUndefined();
  });
  it('passes on the server message for a missing person', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'PERSON_NOT_FOUND', message: 'Person not found', fieldErrors: {} }), { status: 404 })));
    await expect(apiClient('/api/persons/999')).rejects.toMatchObject({ code: 'PERSON_NOT_FOUND', message: 'Person not found', status: 404 });
  });
  it('keeps field feedback from malformed-input errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'INVALID_REQUEST', message: 'Enter the date of birth as a valid YYYY-MM-DD date', fieldErrors: { dateOfBirth: 'Enter a valid calendar date' } }), { status: 400 })));
    const failure: unknown = await apiClient('/api/persons', { method: 'POST' }).catch((reason: unknown) => reason);
    expect(failure).toBeInstanceOf(ApiError);
    expect((failure as ApiError).fieldErrors).toEqual({ dateOfBirth: 'Enter a valid calendar date' });
  });
});

describe('Saved notice', () => {
  it('is shown once, only for the person the server just saved', () => {
    expect(peekSaved('7')).toBeNull();
    rememberSaved(7, 'updated');
    expect(peekSaved('8')).toBeNull();
    expect(peekSaved('7')).toBe('updated');
    clearSaved('7');
    expect(peekSaved('7')).toBeNull();
  });
});
