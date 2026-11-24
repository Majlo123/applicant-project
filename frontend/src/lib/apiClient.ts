import type { FieldErrors } from './personApi';

export class ApiError extends Error {
  constructor(message: string, public code: string, public fieldErrors: FieldErrors = {}, public status = 0) {
    super(message);
  }
}

export async function apiClient<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...options, cache: 'no-store',
      headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
    });
  } catch {
    throw new ApiError('Unable to connect. Check your connection and try again.', 'NETWORK_ERROR');
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const fields: FieldErrors = {};
    for (const key of ['firstName', 'lastName', 'email', 'dateOfBirth'] as const) {
      if (typeof body?.fieldErrors?.[key] === 'string') fields[key] = body.fieldErrors[key];
    }
    throw new ApiError(typeof body?.message === 'string' ? body.message : 'Unable to complete the request. Please try again.',
      typeof body?.code === 'string' ? body.code : 'REQUEST_FAILED', fields, response.status);
  }
  // DELETE answers 204 No Content: there is no body to parse.
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
