import { Box, FormControlLabel, Stack, Switch, TextField, Typography } from '@mui/material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import dayjs, { Dayjs } from 'dayjs';
import { ParameterDescriptor } from '../types';

export type ParamValues = Record<string, unknown>;
export type ParamErrors = Record<string, string | undefined>;

interface Props {
  schema: ParameterDescriptor[];
  values: ParamValues;
  errors?: ParamErrors;
  onChange: (next: ParamValues) => void;
  /** Field-name -> disabled. Used by edit-mode to lock immutable params (e.g. alias). */
  disabledFields?: Record<string, boolean>;
}

/**
 * Renders a list of form inputs derived from a strategy's `parameterSchema`.
 * The parent owns the values object and is responsible for plumbing them into
 * the eventual `parameters` payload sent to the backend.
 *
 * Field-type mapping:
 *   - string  → TextField
 *   - number  → TextField type="number" with min/max
 *   - boolean → Switch
 *   - date    → MUI DatePicker
 */
export function DynamicParamFields({
  schema,
  values,
  errors = {},
  onChange,
  disabledFields = {},
}: Props) {
  if (!schema || schema.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        This strategy has no additional parameters.
      </Typography>
    );
  }

  const set = (name: string, value: unknown) => {
    onChange({ ...values, [name]: value });
  };

  return (
    <Stack spacing={2}>
      {schema.map((param) => {
        const raw = values[param.name];
        const fallback = raw === undefined || raw === null || raw === '' ? param.default : raw;
        const fieldError = errors[param.name];
        const disabled = disabledFields[param.name] ?? false;
        const label = `${param.name}${param.required ? ' *' : ''}`;
        const helperText =
          fieldError ??
          [
            param.description,
            param.pattern ? `Pattern: ${param.pattern}` : null,
            param.type === 'number' && (param.min !== undefined || param.max !== undefined)
              ? `Range: ${param.min ?? '−∞'} – ${param.max ?? '∞'}`
              : null,
            param.default !== undefined && param.default !== null
              ? `Default: ${String(param.default)}`
              : null,
          ]
            .filter(Boolean)
            .join(' · ');

        switch (param.type) {
          case 'boolean':
            return (
              <Box key={param.name}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={Boolean(fallback)}
                      onChange={(e) => set(param.name, e.target.checked)}
                      disabled={disabled}
                    />
                  }
                  label={label}
                />
                {helperText ? (
                  <Typography variant="caption" color={fieldError ? 'error' : 'text.secondary'}>
                    {helperText}
                  </Typography>
                ) : null}
              </Box>
            );

          case 'number':
            return (
              <TextField
                key={param.name}
                label={label}
                type="number"
                value={fallback ?? ''}
                onChange={(e) => {
                  const v = e.target.value;
                  set(param.name, v === '' ? undefined : Number(v));
                }}
                required={param.required}
                disabled={disabled}
                error={Boolean(fieldError)}
                helperText={helperText}
                inputProps={{ min: param.min, max: param.max, step: 1 }}
                fullWidth
              />
            );

          case 'date':
            return (
              <DatePicker
                key={param.name}
                label={label}
                value={fallback ? dayjs(fallback as string) : null}
                onChange={(next: Dayjs | null) => set(param.name, next ? next.toISOString() : undefined)}
                disabled={disabled}
                slotProps={{
                  textField: {
                    required: param.required,
                    fullWidth: true,
                    error: Boolean(fieldError),
                    helperText,
                  },
                }}
              />
            );

          case 'string':
          default:
            return (
              <TextField
                key={param.name}
                label={label}
                value={(fallback as string) ?? ''}
                onChange={(e) => set(param.name, e.target.value)}
                required={param.required}
                disabled={disabled}
                error={Boolean(fieldError)}
                helperText={helperText}
                fullWidth
                inputProps={param.pattern ? { pattern: param.pattern } : undefined}
              />
            );
        }
      })}
    </Stack>
  );
}
