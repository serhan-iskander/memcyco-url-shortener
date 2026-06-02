import {
  CircularProgress,
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  SelectChangeEvent,
} from '@mui/material';
import { useStrategies } from '../hooks';
import { StrategyDescriptor } from '../types';

interface Props {
  value: string;
  onChange: (name: string, descriptor: StrategyDescriptor | null) => void;
  disabled?: boolean;
  error?: string;
  required?: boolean;
}

export function StrategySelect({
  value,
  onChange,
  disabled = false,
  error,
  required = true,
}: Props) {
  const { data: strategies, isLoading, isError } = useStrategies();

  const handleChange = (e: SelectChangeEvent<string>) => {
    const next = e.target.value;
    const descriptor = strategies?.find((s) => s.name === next) ?? null;
    onChange(next, descriptor);
  };

  const helperText =
    error ??
    (isError
      ? 'Could not load strategies'
      : isLoading
        ? 'Loading strategies…'
        : 'Determines how the short code is generated');

  return (
    <FormControl fullWidth required={required} error={Boolean(error) || isError} disabled={disabled}>
      <InputLabel id="strategy-select-label">Strategy</InputLabel>
      <Select
        labelId="strategy-select-label"
        id="strategy-select"
        label="Strategy"
        value={strategies?.some((s) => s.name === value) ? value : ''}
        onChange={handleChange}
        endAdornment={isLoading ? <CircularProgress size={18} sx={{ mr: 3 }} /> : null}
      >
        {(strategies ?? []).map((s) => (
          <MenuItem key={s.name} value={s.name}>
            {s.displayName}
            {s.description ? (
              <span style={{ color: 'rgba(0,0,0,0.5)', marginLeft: 8, fontSize: 12 }}>
                — {s.description}
              </span>
            ) : null}
          </MenuItem>
        ))}
      </Select>
      {helperText ? <FormHelperText>{helperText}</FormHelperText> : null}
    </FormControl>
  );
}
