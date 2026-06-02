import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  Grid,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SaveIcon from '@mui/icons-material/Save';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import dayjs, { Dayjs } from 'dayjs';
import {
  DynamicParamFields,
  ParamErrors,
  ParamValues,
  StrategySelect,
  TagsInput,
} from '../components';
import {
  useCreateShortLink,
  useShortLink,
  useStrategies,
  useUpdateShortLink,
} from '../hooks';
import { useSnackbar } from '../snackbar/SnackbarProvider';
import {
  ApiError,
  CreateShortLinkRequest,
  ParameterDescriptor,
  StrategyDescriptor,
  UpdateShortLinkRequest,
} from '../types';
import {
  ALIAS_REGEX,
  validateAlias,
  validateFutureDate,
  validateMaxClicks,
  validateOriginalUrl,
  validateTags,
} from '../utils/validation';

type Mode = 'create' | 'edit';

interface Props {
  /**
   * Optional explicit mode. When omitted, mode is derived from the route:
   * presence of an `:id` param means edit, otherwise create.
   */
  mode?: Mode;
}

interface FormState {
  originalUrl: string;
  strategy: string;
  expiresAt: string | null;
  maxClicks: string;
  tags: string[];
  parameters: ParamValues;
}

const EMPTY_FORM: FormState = {
  originalUrl: '',
  strategy: '',
  expiresAt: null,
  maxClicks: '',
  tags: [],
  parameters: {},
};

interface FormErrors {
  originalUrl?: string;
  strategy?: string;
  expiresAt?: string;
  maxClicks?: string;
  tags?: string;
  parameters?: ParamErrors;
  form?: string;
}

function paramsWithDefaults(schema: ParameterDescriptor[] | undefined, current: ParamValues): ParamValues {
  const next: ParamValues = { ...current };
  (schema ?? []).forEach((p) => {
    if (next[p.name] === undefined && p.default !== undefined && p.default !== null) {
      next[p.name] = p.default;
    }
  });
  return next;
}

function validateParameters(
  schema: ParameterDescriptor[] | undefined,
  values: ParamValues,
): ParamErrors {
  const errs: ParamErrors = {};
  (schema ?? []).forEach((p) => {
    const v = values[p.name];
    const isEmpty = v === undefined || v === null || v === '';
    if (p.required && isEmpty) {
      errs[p.name] = `${p.name} is required`;
      return;
    }
    if (!isEmpty && p.type === 'number') {
      const n = Number(v);
      if (!Number.isFinite(n)) {
        errs[p.name] = 'Must be a number';
      } else if (p.min !== undefined && n < p.min) {
        errs[p.name] = `Must be ≥ ${p.min}`;
      } else if (p.max !== undefined && n > p.max) {
        errs[p.name] = `Must be ≤ ${p.max}`;
      }
    }
    if (!isEmpty && p.type === 'string' && p.name === 'alias') {
      if (!ALIAS_REGEX.test(String(v))) {
        errs[p.name] = 'Alias must be 3–32 chars (letters, digits, _ or -)';
      }
    }
  });
  return errs;
}

export function ShortLinkFormPage({ mode }: Props) {
  const navigate = useNavigate();
  const snackbar = useSnackbar();
  const { id } = useParams<{ id: string }>();
  const numericId = id ? Number(id) : undefined;
  const resolvedMode: Mode = mode ?? (numericId !== undefined ? 'edit' : 'create');
  const isEdit = resolvedMode === 'edit';

  const { data: strategies } = useStrategies();
  const existingQuery = useShortLink(isEdit ? numericId : undefined);
  const createMutation = useCreateShortLink();
  const updateMutation = useUpdateShortLink(numericId ?? 0);

  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [errors, setErrors] = useState<FormErrors>({});
  const [hydrated, setHydrated] = useState<boolean>(false);

  // When editing, hydrate the form from the loaded resource.
  useEffect(() => {
    if (!isEdit) {
      setHydrated(true);
      return;
    }
    if (existingQuery.data && !hydrated) {
      const r = existingQuery.data;
      setForm({
        originalUrl: r.originalUrl,
        strategy: r.strategy,
        expiresAt: r.expiresAt ?? null,
        maxClicks: r.maxClicks !== null && r.maxClicks !== undefined ? String(r.maxClicks) : '',
        tags: r.tags ?? [],
        parameters: (r.parameters as ParamValues) ?? {},
      });
      setHydrated(true);
    }
  }, [isEdit, existingQuery.data, hydrated]);

  const selectedStrategy: StrategyDescriptor | undefined = useMemo(
    () => strategies?.find((s) => s.name === form.strategy),
    [strategies, form.strategy],
  );

  const handleStrategyChange = (name: string, descriptor: StrategyDescriptor | null) => {
    setForm((prev) => ({
      ...prev,
      strategy: name,
      parameters: paramsWithDefaults(descriptor?.parameterSchema, {}),
    }));
    setErrors((prev) => ({ ...prev, strategy: undefined, parameters: undefined }));
  };

  const validateAll = (): FormErrors => {
    const next: FormErrors = {};
    next.originalUrl = validateOriginalUrl(form.originalUrl) ?? undefined;
    if (!isEdit && !form.strategy) next.strategy = 'Strategy is required';
    next.expiresAt = !isEdit ? (validateFutureDate(form.expiresAt) ?? undefined) : undefined;
    next.tags = validateTags(form.tags) ?? undefined;

    const maxClicksNum = form.maxClicks === '' ? null : Number(form.maxClicks);
    next.maxClicks = validateMaxClicks(maxClicksNum) ?? undefined;

    if (!isEdit) {
      const paramErrors = validateParameters(selectedStrategy?.parameterSchema, form.parameters);
      if (form.strategy === 'CUSTOM_ALIAS') {
        const aliasValue = form.parameters.alias;
        const aliasErr = validateAlias(typeof aliasValue === 'string' ? aliasValue : '');
        if (aliasErr) paramErrors.alias = aliasErr;
      }
      if (Object.keys(paramErrors).length > 0) {
        next.parameters = paramErrors;
      }
    }
    return next;
  };

  const distributeServerErrors = (err: ApiError) => {
    const next: FormErrors = {};
    err.fieldErrors.forEach((fe) => {
      switch (fe.field) {
        case 'originalUrl':
          next.originalUrl = fe.message;
          break;
        case 'expiresAt':
          next.expiresAt = fe.message;
          break;
        case 'maxClicks':
          next.maxClicks = fe.message;
          break;
        case 'tags':
          next.tags = fe.message;
          break;
        case 'alias':
          next.parameters = { ...(next.parameters ?? {}), alias: fe.message };
          break;
        default:
          if (fe.field.startsWith('parameters.')) {
            const key = fe.field.slice('parameters.'.length);
            next.parameters = { ...(next.parameters ?? {}), [key]: fe.message };
          } else {
            next.form = `${fe.field}: ${fe.message}`;
          }
      }
    });
    if (err.status === 409) {
      next.parameters = {
        ...(next.parameters ?? {}),
        alias: err.detail || 'Already in use',
      };
    }
    if (!next.form && err.detail && err.fieldErrors.length === 0) {
      next.form = err.detail;
    }
    setErrors(next);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const validation = validateAll();
    setErrors(validation);
    const hasErrors =
      validation.originalUrl ||
      validation.strategy ||
      validation.expiresAt ||
      validation.maxClicks ||
      validation.tags ||
      (validation.parameters && Object.values(validation.parameters).some(Boolean));
    if (hasErrors) return;

    const maxClicksNum = form.maxClicks === '' ? null : Number(form.maxClicks);

    try {
      if (isEdit && numericId) {
        const body: UpdateShortLinkRequest = {
          originalUrl: form.originalUrl,
          expiresAt: form.expiresAt,
          maxClicks: maxClicksNum,
          tags: form.tags,
        };
        await updateMutation.mutateAsync(body);
        snackbar.success('Short link updated');
        navigate('/');
      } else {
        const body: CreateShortLinkRequest = {
          originalUrl: form.originalUrl,
          strategy: form.strategy,
          expiresAt: form.expiresAt,
          maxClicks: maxClicksNum,
          tags: form.tags,
          parameters: form.parameters,
        };
        if (form.strategy === 'CUSTOM_ALIAS' && typeof form.parameters.alias === 'string') {
          body.alias = form.parameters.alias;
        }
        const result = await createMutation.mutateAsync(body);
        snackbar.success(`Created ${result.shortCode}`);
        navigate('/');
      }
    } catch (err) {
      if (err instanceof ApiError) {
        distributeServerErrors(err);
        snackbar.error(err.detail || err.title);
      } else {
        const message = err instanceof Error ? err.message : 'Unknown error';
        setErrors({ form: message });
        snackbar.error(message);
      }
    }
  };

  const isLoading =
    (isEdit && existingQuery.isLoading) ||
    createMutation.isPending ||
    updateMutation.isPending;

  if (isEdit && existingQuery.isError) {
    return (
      <Alert severity="error">Failed to load short link.</Alert>
    );
  }

  if (isEdit && !hydrated) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Stack spacing={3} component="form" onSubmit={handleSubmit} noValidate>
      <Stack direction="row" alignItems="center" spacing={1}>
        <Button component={RouterLink} to="/" startIcon={<ArrowBackIcon />} color="inherit">
          Back
        </Button>
      </Stack>
      <Box>
        <Typography variant="h4">
          {isEdit ? 'Edit short link' : 'Create short link'}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {isEdit
            ? 'Update the destination, expiration, click limit, or tags. The short code is immutable.'
            : 'Pick a generation strategy and tell us where the short link should point.'}
        </Typography>
      </Box>

      {errors.form ? <Alert severity="error">{errors.form}</Alert> : null}

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Grid container spacing={3}>
          <Grid item xs={12}>
            <TextField
              label="Original URL"
              placeholder="https://example.com/some/long/path"
              value={form.originalUrl}
              onChange={(e) => setForm((p) => ({ ...p, originalUrl: e.target.value }))}
              required
              fullWidth
              error={Boolean(errors.originalUrl)}
              helperText={errors.originalUrl ?? 'Must start with http:// or https://'}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <StrategySelect
              value={form.strategy}
              onChange={handleStrategyChange}
              disabled={isEdit}
              error={errors.strategy}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <DateTimePicker
              label="Expires at (optional)"
              value={form.expiresAt ? dayjs(form.expiresAt) : null}
              onChange={(next: Dayjs | null) =>
                setForm((p) => ({ ...p, expiresAt: next ? next.toISOString() : null }))
              }
              slotProps={{
                textField: {
                  fullWidth: true,
                  error: Boolean(errors.expiresAt),
                  helperText: errors.expiresAt ?? 'Leave empty to never expire',
                },
              }}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              label="Max clicks (optional)"
              type="number"
              value={form.maxClicks}
              onChange={(e) => setForm((p) => ({ ...p, maxClicks: e.target.value }))}
              inputProps={{ min: 1, max: 1_000_000, step: 1 }}
              fullWidth
              error={Boolean(errors.maxClicks)}
              helperText={errors.maxClicks ?? 'Cap how many times the link can be followed'}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TagsInput
              value={form.tags}
              onChange={(next) => setForm((p) => ({ ...p, tags: next }))}
              error={errors.tags}
            />
          </Grid>

          {!isEdit && selectedStrategy ? (
            <>
              <Grid item xs={12}>
                <Divider />
              </Grid>
              <Grid item xs={12}>
                <Typography variant="h6" sx={{ mb: 1 }}>
                  {selectedStrategy.displayName} parameters
                </Typography>
                <DynamicParamFields
                  schema={selectedStrategy.parameterSchema}
                  values={form.parameters}
                  errors={errors.parameters}
                  onChange={(next) => setForm((p) => ({ ...p, parameters: next }))}
                />
              </Grid>
            </>
          ) : null}
        </Grid>
      </Paper>

      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button component={RouterLink} to="/" color="inherit">
          Cancel
        </Button>
        <Button
          type="submit"
          variant="contained"
          startIcon={isLoading ? <CircularProgress color="inherit" size={18} /> : <SaveIcon />}
          disabled={isLoading}
        >
          {isEdit ? 'Save changes' : 'Create short link'}
        </Button>
      </Stack>
    </Stack>
  );
}
