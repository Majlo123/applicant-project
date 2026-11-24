import type { FieldErrors, PersonInput } from '@/lib/personApi';

// Same rules as the backend: letters and combining marks from any script, spaces, apostrophes (' ’) and hyphens (- ‐).
const NAME = /^[\p{L}\p{M}\p{Zs}'’\-‐]*$/u;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/u;

/** Length in characters (Unicode code points), not UTF-16 units, so emoji and rare letters count as one. */
export const characterCount = (text: string) => [...text].length;

export const emptyPerson: PersonInput = { firstName: '', lastName: '', email: '', dateOfBirth: '' };
export function normalizePerson(value: PersonInput): PersonInput {
  return { firstName: value.firstName.trim(), lastName: value.lastName.trim(), email: value.email.trim(), dateOfBirth: value.dateOfBirth };
}

/**
 * Validates the form values. `dateUnreadable` is true when the browser's date field holds a partial or impossible
 * date (for example 02/31), which the browser reports as an empty value.
 */
export function validatePerson(input: PersonInput, today = new Date().toISOString().slice(0, 10), dateUnreadable = false): FieldErrors {
  const value = normalizePerson(input);
  const errors: FieldErrors = {};
  for (const field of ['firstName', 'lastName'] as const) {
    if (!value[field]) errors[field] = 'Enter a ' + (field === 'firstName' ? 'first' : 'last') + ' name';
    else if (characterCount(value[field]) > 100) errors[field] = 'Use 100 characters or fewer';
    else if (!NAME.test(value[field])) errors[field] = 'Use letters, spaces, apostrophes and hyphens only';
  }
  if (!value.email) errors.email = 'Enter an email address';
  else if (characterCount(value.email) > 254) errors.email = 'Use 254 characters or fewer';
  else if (!EMAIL.test(value.email)) errors.email = 'Enter a valid email address';
  const date = value.dateOfBirth;
  const parsed = new Date(`${date}T00:00:00Z`);
  if (!date) errors.dateOfBirth = dateUnreadable ? 'Enter a valid calendar date' : 'Enter a date of birth';
  else if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || date < '0001-01-01' || !Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) errors.dateOfBirth = 'Enter a valid calendar date';
  else if (date > today) errors.dateOfBirth = 'Date of birth cannot be in the future';
  return errors;
}
