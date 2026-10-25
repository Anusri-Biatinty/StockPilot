import axios from 'axios';
import type { ApiErrorBody } from '../api/types';

export function apiError(error: unknown): ApiErrorBody | null {
  if (axios.isAxiosError(error) && error.response?.data && typeof error.response.data === 'object') {
    const data = error.response.data as Partial<ApiErrorBody>;
    if (data.message) {
      return { status: error.response.status, code: data.code ?? 'ERROR', message: data.message, fieldErrors: data.fieldErrors };
    }
  }
  return null;
}

/** A user-facing message for any thrown error. */
export function errorMessage(error: unknown): string {
  const body = apiError(error);
  if (body) {
    if (body.fieldErrors?.length) {
      return body.fieldErrors.map((f) => `${f.field}: ${f.message}`).join('; ');
    }
    return body.message;
  }
  if (axios.isAxiosError(error)) {
    if (!error.response) return 'Cannot reach the server. Check that the backend is running.';
    if (error.response.status === 429) return 'Too many attempts. Please wait a minute and try again.';
    return `Request failed (${error.response.status})`;
  }
  return error instanceof Error ? error.message : 'Something went wrong';
}

/** Field-level errors keyed by field name, for inline form messages. */
export function fieldErrors(error: unknown): Record<string, string> {
  const result: Record<string, string> = {};
  apiError(error)?.fieldErrors?.forEach((f) => {
    result[f.field] ??= f.message;
  });
  return result;
}
