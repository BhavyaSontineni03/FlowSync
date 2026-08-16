import { alpha } from '@mui/material/styles'

/**
 * 8pt spacing rhythm (Apple-like).
 * MUI `theme.spacing(n)` = n × 8px (see theme.js). Prefer these tokens over one-off mb/mt/gap.
 *
 * | Token            | Theme units | px        |
 * |------------------|-------------|-----------|
 * | base             | 1           | 8         |
 * | page padding     | 2 / 3 / 4   | 16/24/32  |
 * | section gap      | 3           | 24        |
 * | card padding     | 2 / 2.5     | 16 / 20   |
 * | stack (tight→loose) | 1 / 1.5 / 2 | 8 / 12 / 16 |
 */
export const SPACE = {
  base: 8,
  /** Page padding by breakpoint (16 / 24 / 32). */
  page: { xs: 2, sm: 3, md: 4 },
  /** Gap between major page sections. */
  section: 3,
  /** Card / surface internal padding. */
  card: 2,
  cardComfortable: 2.5,
  /** Related control / text stacks. */
  stackTight: 1,
  stack: 1.5,
  stackLoose: 2,
}

/** Soft control/card radius matching theme components (~10px). Prefer px strings: bare numbers are × shape.borderRadius. */
export const softRadius = '10px'

/** Page content padding: 16 / 24 / 32. */
export const pageSx = { p: SPACE.page }
/** @deprecated Prefer pageSx. Same value. */
export const pageWrapSx = pageSx

/** Vertical / flex gap between major sections (24px). */
export const sectionGap = SPACE.section

/** CardContent-friendly padding (16px). */
export const cardPad = SPACE.card
export const cardPadSx = {
  p: SPACE.card,
  '&:last-child': { pb: SPACE.card },
}
export const cardPadComfortableSx = {
  p: SPACE.cardComfortable,
  '&:last-child': { pb: SPACE.cardComfortable },
}

/** Stack gaps: 8 / 12 / 16. */
export const stackGapTight = SPACE.stackTight
export const stackGap = SPACE.stack
export const stackGapLoose = SPACE.stackLoose

/** Resolve 'primary.main' / hex / rgb for APIs that need raw CSS colors (alpha, recharts). */
export function toCssColor(theme, value) {
  if (!value || typeof value !== 'string') return value
  if (value.startsWith('#') || value.startsWith('rgb') || value.startsWith('hsl')) return value
  const [group, shade = 'main'] = value.split('.')
  const swatch = theme?.palette?.[group]
  if (!swatch) return value
  if (typeof swatch === 'string') return swatch
  return swatch[shade] || swatch.main || value
}

/** Soft fill from a palette key (primary, secondary, success, error, warning, info). */
export function paletteTone(theme, key = 'primary') {
  const swatch = theme.palette[key] || theme.palette.primary
  return {
    color: swatch.main,
    bg: alpha(swatch.main, theme.palette.mode === 'dark' ? 0.2 : 0.12),
  }
}

/** IconButton / action well from palette (primary, success, warning, error, info, secondary). */
export function actionIconSx(theme, key = 'primary') {
  const { color, bg } = paletteTone(theme, key)
  const hover = alpha(color, theme.palette.mode === 'dark' ? 0.32 : 0.22)
  return {
    backgroundColor: bg,
    color,
    '&:hover': { backgroundColor: hover },
  }
}

const STATUS_KEY = {
  PENDING: 'warning',
  DRAFT: 'warning',
  SUBMITTED: 'info',
  PROCESSED: 'info',
  APPROVED: 'success',
  PAID: 'success',
  ACTIVE: 'success',
  COMPLETED: 'info',
  REJECTED: 'error',
  CANCELLED: 'error',
  ON_HOLD: 'warning',
  INACTIVE: 'secondary',
  WORK: 'primary',
  LEAVE: 'info',
  HOLIDAY: 'secondary',
  USER_CREATE: 'primary',
  USER_UPDATE: 'info',
  USER_DELETE: 'error',
  PROJECT_CREATE: 'success',
  PROJECT_UPDATE: 'info',
  PROJECT_DELETE: 'error',
}

const ROLE_KEY = {
  ADMIN: 'error',
  MANAGER: 'primary',
  HR: 'warning',
  FINANCE: 'info',
  EMPLOYEE: 'secondary',
}

/** Status / workflow chip colors from theme palette. */
export function statusTone(theme, status) {
  return paletteTone(theme, STATUS_KEY[status] || 'secondary')
}

export function roleTone(theme, role) {
  return paletteTone(theme, ROLE_KEY[role] || 'secondary')
}

/** Chart categorical series aligned to the sage/slate system. */
export function chartColors(theme) {
  const { primary, secondary, warning, error, info, success } = theme.palette
  return [
    primary.main,
    secondary.main,
    info.main,
    warning.main,
    error.main,
    success.light,
    primary.light,
    warning.dark,
  ]
}

export const pageTitleSx = {
  fontWeight: 600,
  fontSize: { xs: '1.75rem', md: '2rem' },
  mb: 0.5,
  color: 'text.primary',
}

export const pageSubtitleSx = {
  color: 'text.secondary',
  lineHeight: 1.45,
}

/** Page header row: title block + actions, then sectionGap below. */
export const pageHeaderSx = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  mb: SPACE.section,
  flexWrap: 'wrap',
  gap: SPACE.stackLoose,
}

export function tableHeadCellSx(theme) {
  return {
    fontWeight: 600,
    letterSpacing: '0.04em',
    py: 2,
    px: 3,
    fontSize: '0.75rem',
    textTransform: 'uppercase',
    color: 'text.secondary',
    borderBottom: `1px solid ${theme.palette.divider}`,
  }
}

export function iconWellSx(theme, key = 'primary') {
  const { color, bg } = paletteTone(theme, key)
  return {
    width: 48,
    height: 48,
    borderRadius: 2,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: bg,
    color,
    flexShrink: 0,
  }
}

export function quietTabsSx(theme) {
  return {
    mb: SPACE.section,
    minHeight: 48,
    '& .MuiTabs-indicator': {
      height: 2,
      borderRadius: 1,
      backgroundColor: theme.palette.primary.main,
    },
    '& .MuiTab-root': {
      minHeight: 48,
      textTransform: 'none',
      fontWeight: 600,
      fontSize: '0.9375rem',
      color: 'text.secondary',
      '&.Mui-selected': {
        color: 'primary.main',
      },
    },
  }
}

/** Chip sx from a status, role, or palette key. */
export function chipSx(theme, key) {
  const resolved = STATUS_KEY[key]
    ? statusTone(theme, key)
    : ROLE_KEY[key]
      ? roleTone(theme, key)
      : paletteTone(theme, key)
  return {
    backgroundColor: resolved.bg,
    color: resolved.color,
    fontWeight: 600,
    fontSize: '0.75rem',
  }
}
