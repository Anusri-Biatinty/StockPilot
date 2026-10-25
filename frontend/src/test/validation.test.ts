import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import type { Product } from '../api/types';
import { newLine, validateLines } from '../components/ui/LineItemsEditor';
import { errorMessage, fieldErrors } from '../lib/errors';
import { humanize } from '../lib/format';
import { validatePassword } from '../pages/auth/RegisterPage';
import { validateProduct } from '../pages/catalog/ProductFormModal';

const valid = { sku: 'ABC-1', name: 'Widget', description: '', categoryId: '1', unitPrice: '10.50', costPrice: '4', reorderThreshold: '5', imageUrl: '' };

describe('validateProduct', () => {
  it('accepts a valid product', () => {
    expect(validateProduct(valid)).toEqual({});
  });

  it('rejects negative prices, extra decimals, negative thresholds and unsafe URLs', () => {
    const errors = validateProduct({ ...valid, unitPrice: '-1', costPrice: '1.999', reorderThreshold: '-2', imageUrl: 'javascript:alert(1)', sku: 'a' });
    expect(Object.keys(errors).sort()).toEqual(['costPrice', 'imageUrl', 'reorderThreshold', 'sku', 'unitPrice']);
  });

  it('allows a blank SKU so the server can generate one', () => {
    expect(validateProduct({ ...valid, sku: '' }).sku).toBeUndefined();
  });
});

describe('validatePassword', () => {
  it('mirrors the backend password policy', () => {
    expect(validatePassword('Password1')).toBeUndefined();
    expect(validatePassword('short1')).toBeDefined();
    expect(validatePassword('lettersonly')).toBeDefined();
  });
});

describe('validateLines', () => {
  it('flags missing products and bad quantities', () => {
    const p = { id: 1, sku: 'A', name: 'A', costPrice: 1, unitPrice: 2 } as Product;
    const lines = [newLine(null), newLine(p, '0')];
    const result = validateLines(lines);
    expect(result.lineErrors[lines[0].key]).toBe('Choose a product');
    expect(result.message).toMatch(/whole number/);
    expect(validateLines([newLine(p, '3')])).toEqual({ lineErrors: {}, message: undefined });
  });
});

describe('error helpers', () => {
  const axiosError = (status: number, data: unknown) => {
    const headers = new AxiosHeaders();
    return new AxiosError('fail', 'ERR', { headers }, null, { status, statusText: '', headers, config: { headers }, data });
  };

  it('uses the API message and field errors', () => {
    const err = axiosError(409, { status: 409, code: 'INSUFFICIENT_STOCK', message: 'Insufficient stock for X' });
    expect(errorMessage(err)).toBe('Insufficient stock for X');
    const validation = axiosError(400, { message: 'invalid', fieldErrors: [{ field: 'name', message: 'must not be blank' }] });
    expect(fieldErrors(validation)).toEqual({ name: 'must not be blank' });
  });

  it('explains network failures', () => {
    expect(errorMessage(new AxiosError('Network Error'))).toMatch(/Cannot reach the server/);
  });

  it('humanizes enum values', () => {
    expect(humanize('PARTIALLY_RECEIVED')).toBe('Partially received');
  });
});
