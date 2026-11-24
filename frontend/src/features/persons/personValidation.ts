import type { FieldErrors, PersonInput } from '@/lib/personApi';

export const emptyPerson: PersonInput = { firstName: '', lastName: '', email: '', dateOfBirth: '' };
export function normalizePerson(value: PersonInput): PersonInput {
  return { firstName: value.firstName.trim(), lastName: value.lastName.trim(), email: value.email.trim(), dateOfBirth: value.dateOfBirth };
}
export function validatePerson(input: PersonInput, today = new Date().toISOString().slice(0, 10)): FieldErrors {
  const value = normalizePerson(input);
  const errors: FieldErrors = {};
  for (const field of ['firstName', 'lastName'] as const) {
    if (!value[field]) errors[field] = 'Enter a ' + (field === 'firstName' ? 'first' : 'last') + ' name';
    else if (value[field].length > 100) errors[field] = 'Use 100 characters or fewer';
    else if (!/^[A-Za-z ]+$/.test(value[field])) errors[field] = 'Use letters and spaces only';
  }
  if (!value.email) errors.email = 'Enter an email address';
  else if (value.email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.email)) errors.email = 'Enter a valid email address';
  const date = value.dateOfBirth;
  const parsed = new Date(`${date}T00:00:00Z`);
  if (!date) errors.dateOfBirth = 'Enter a date of birth';
  else if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) errors.dateOfBirth = 'Enter a valid calendar date';
  else if (date > today) errors.dateOfBirth = 'Date of birth cannot be in the future';
  return errors;
}
