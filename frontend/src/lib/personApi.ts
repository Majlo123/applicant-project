import { apiClient } from './apiClient';

export type PersonInput = { firstName: string; lastName: string; email: string; dateOfBirth: string };
export type Person = PersonInput & { id: number };
export type FieldErrors = Partial<Record<keyof PersonInput, string>>;
export const personApi = {
  list: () => apiClient<Person[]>('/api/persons'),
  get: (id: string) => apiClient<Person>(`/api/persons/${encodeURIComponent(id)}`),
  create: (person: PersonInput) => apiClient<Person>('/api/persons', { method: 'POST', body: JSON.stringify(person) }),
  update: (id: string, person: PersonInput) => apiClient<Person>(`/api/persons/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(person) }),
  remove: (id: number) => apiClient<void>(`/api/persons/${id}`, { method: 'DELETE' }),
};
