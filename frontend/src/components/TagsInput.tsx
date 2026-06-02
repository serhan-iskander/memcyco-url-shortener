import { Autocomplete, Chip, TextField } from '@mui/material';
import { validateTag } from '../utils/validation';

interface Props {
  value: string[];
  onChange: (next: string[]) => void;
  label?: string;
  placeholder?: string;
  error?: string;
  disabled?: boolean;
}

/**
 * Chip-based tag editor backed by MUI Autocomplete in `freeSolo + multiple` mode.
 * Validates each tag client-side against the same regex the backend enforces.
 */
export function TagsInput({
  value,
  onChange,
  label = 'Tags',
  placeholder = 'Add a tag and press Enter',
  error,
  disabled = false,
}: Props) {
  return (
    <Autocomplete
      multiple
      freeSolo
      disabled={disabled}
      value={value}
      onChange={(_, next) => {
        // De-dupe + drop empties.
        const cleaned = Array.from(new Set(next.map((v) => String(v).trim()).filter(Boolean)));
        onChange(cleaned);
      }}
      options={[]}
      renderTags={(values, getTagProps) =>
        values.map((option, index) => {
          const tagError = validateTag(option);
          const { key, ...tagProps } = getTagProps({ index });
          return (
            <Chip
              key={key}
              label={option}
              size="small"
              color={tagError ? 'error' : 'default'}
              {...tagProps}
            />
          );
        })
      }
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder={placeholder}
          error={Boolean(error)}
          helperText={error ?? 'Press Enter to add. Letters, digits, _ or -. Up to 10 tags.'}
        />
      )}
    />
  );
}
