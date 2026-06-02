/**
 * DynamicParamFields — renders one input per `ParameterDescriptor`, handles
 * defaults, required-field errors, and per-type min/max validation.
 *
 * Real prop shape:
 *   { schema: ParameterDescriptor[];
 *     values: Record<string, unknown>;
 *     errors?: Record<string, string | undefined>;
 *     onChange: (next: ParamValues) => void;
 *     disabledFields?: Record<string, boolean>; }
 *
 * Labels include a "*" suffix for required fields.
 */
import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '../test/renderWithProviders';
import { DynamicParamFields } from './DynamicParamFields';
import type { ParameterDescriptor } from '../types';

describe('<DynamicParamFields>', () => {
  it('renders a text input for a string parameter', () => {
    const schema: ParameterDescriptor[] = [
      { name: 'salt', type: 'string', required: false, default: 'hello' },
    ];
    renderWithProviders(
      <DynamicParamFields schema={schema} values={{}} onChange={vi.fn()} />,
    );
    const input = screen.getByLabelText(/salt/i);
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute('type', 'text');
  });

  it('renders a numeric input for a number parameter with min/max', () => {
    const schema: ParameterDescriptor[] = [
      { name: 'length', type: 'number', required: false, default: 7, min: 4, max: 16 },
    ];
    renderWithProviders(
      <DynamicParamFields schema={schema} values={{ length: 7 }} onChange={vi.fn()} />,
    );
    const input = screen.getByLabelText(/length/i) as HTMLInputElement;
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute('type', 'number');
    expect(input).toHaveAttribute('min', '4');
    expect(input).toHaveAttribute('max', '16');
  });

  it('renders a switch for a boolean parameter', () => {
    const schema: ParameterDescriptor[] = [
      { name: 'enabled', type: 'boolean', required: false, default: true },
    ];
    renderWithProviders(
      <DynamicParamFields schema={schema} values={{ enabled: true }} onChange={vi.fn()} />,
    );
    const sw = screen.getByRole('checkbox', { name: /enabled/i });
    expect(sw).toBeInTheDocument();
    expect(sw).toBeChecked();
  });

  it('renders a date picker for a date parameter', () => {
    const schema: ParameterDescriptor[] = [
      { name: 'startsOn', type: 'date', required: false },
    ];
    renderWithProviders(
      <DynamicParamFields schema={schema} values={{}} onChange={vi.fn()} />,
    );
    // MUI date pickers render an input element with a label.
    expect(screen.getByLabelText(/startsOn|starts on/i)).toBeInTheDocument();
  });

  it('shows a required error when an error message is supplied', () => {
    const schema: ParameterDescriptor[] = [
      { name: 'alias', type: 'string', required: true },
    ];
    renderWithProviders(
      <DynamicParamFields
        schema={schema}
        values={{ alias: '' }}
        errors={{ alias: 'Required' }}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByText('Required')).toBeInTheDocument();
  });

  it('pre-fills inputs from defaults when value is missing', async () => {
    const schema: ParameterDescriptor[] = [
      { name: 'length', type: 'number', required: false, default: 7 },
    ];
    renderWithProviders(
      <DynamicParamFields schema={schema} values={{}} onChange={vi.fn()} />,
    );
    await waitFor(() => {
      const input = screen.getByLabelText(/length/i) as HTMLInputElement;
      expect(input.value).toBe('7');
    });
  });

  it('invokes onChange with the merged values object when a text value is edited', async () => {
    const schema: ParameterDescriptor[] = [
      { name: 'alias', type: 'string', required: true },
    ];
    const onChange = vi.fn();
    const { user } = renderWithProviders(
      <DynamicParamFields schema={schema} values={{ alias: '' }} onChange={onChange} />,
    );
    await user.type(screen.getByLabelText(/alias/i), 'a');
    // Component calls onChange({ ...values, [name]: value }) per keystroke.
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ alias: 'a' }));
  });

  it('renders an empty-state notice when schema is empty', () => {
    renderWithProviders(
      <DynamicParamFields schema={[]} values={{}} onChange={vi.fn()} />,
    );
    expect(screen.getByText(/no additional parameters/i)).toBeInTheDocument();
  });
});
