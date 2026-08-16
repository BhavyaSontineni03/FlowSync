import { createTheme } from '@mui/material/styles'

// FlowSync visual system: calm sage + slate ink pastels.
// Primary fills stay soft; `main` values clear WCAG AA on white button text.
//
// Spacing contract (8pt grid). Helpers live in utils/uiTokens.js:
//   base 8 · page pad 16/24/32 (p:2/3/4) · section 24 · card 16-20 · stack 8/12/16
// Soft control radii ~10px (see softRadius / MuiButton|Card). Prefer px strings in sx
// when overriding radius; bare numbers are multiplied by shape.borderRadius.
const colors = {
  primary: {
    main: '#4F7A5C',
    light: '#C5D9CB',
    dark: '#355A42',
    contrastText: '#FFFFFF',
  },
  secondary: {
    main: '#4A5568',
    light: '#D5DBE3',
    dark: '#2D3748',
    contrastText: '#FFFFFF',
  },
  success: {
    main: '#4F7A5C',
    light: '#C5D9CB',
    dark: '#355A42',
  },
  error: {
    main: '#C45B6A',
    light: '#F0D0D5',
    dark: '#9A3D4A',
  },
  warning: {
    main: '#B8894A',
    light: '#E8D5B8',
    dark: '#8A652F',
  },
  info: {
    main: '#4A7A8C',
    light: '#C5D8E0',
    dark: '#335A68',
  },
}

const fontBody = '"DM Sans", "Helvetica Neue", Helvetica, Arial, sans-serif'
const fontDisplay = '"Fraunces", "Georgia", serif'

const typography = {
  fontFamily: fontBody,
  h1: { fontFamily: fontDisplay, fontWeight: 600, fontSize: '2.5rem', letterSpacing: '-0.02em', lineHeight: 1.2 },
  h2: { fontFamily: fontDisplay, fontWeight: 600, fontSize: '2rem', letterSpacing: '-0.015em', lineHeight: 1.25 },
  h3: { fontFamily: fontDisplay, fontWeight: 600, fontSize: '1.75rem', letterSpacing: '-0.01em', lineHeight: 1.3 },
  h4: { fontFamily: fontDisplay, fontWeight: 600, fontSize: '1.5rem', letterSpacing: '-0.01em', lineHeight: 1.35 },
  h5: { fontFamily: fontDisplay, fontWeight: 550, fontSize: '1.25rem', letterSpacing: '-0.005em', lineHeight: 1.4 },
  h6: { fontFamily: fontBody, fontWeight: 600, fontSize: '1rem', letterSpacing: '0', lineHeight: 1.4 },
  body1: { fontSize: '1rem', lineHeight: 1.55, letterSpacing: '0' },
  body2: { fontSize: '0.875rem', lineHeight: 1.5, letterSpacing: '0' },
  button: { textTransform: 'none', fontWeight: 600, letterSpacing: '0', fontSize: '0.9375rem' },
}

const softShadows = [
  'none',
  '0 1px 2px rgba(30, 41, 59, 0.06)',
  '0 2px 6px rgba(30, 41, 59, 0.07)',
  '0 4px 12px rgba(30, 41, 59, 0.08)',
  '0 8px 20px rgba(30, 41, 59, 0.09)',
  '0 12px 28px rgba(30, 41, 59, 0.1)',
  ...Array(19).fill('0 12px 28px rgba(30, 41, 59, 0.1)'),
]

const buildComponents = (isDark) => ({
  MuiCssBaseline: {
    styleOverrides: {
      body: {
        backgroundColor: isDark ? '#161B19' : '#F3F6F4',
        backgroundImage: isDark
          ? 'radial-gradient(ellipse at 12% 0%, rgba(79, 122, 92, 0.12) 0%, transparent 50%), radial-gradient(ellipse at 88% 100%, rgba(74, 85, 104, 0.14) 0%, transparent 45%)'
          : 'radial-gradient(ellipse at 10% 0%, rgba(197, 217, 203, 0.55) 0%, transparent 48%), radial-gradient(ellipse at 90% 100%, rgba(213, 219, 227, 0.45) 0%, transparent 42%)',
        backgroundAttachment: 'fixed',
        minHeight: '100vh',
      },
    },
  },
  MuiButton: {
    styleOverrides: {
      root: {
        borderRadius: 10,
        padding: '10px 20px',
        boxShadow: 'none',
        transition: 'opacity 0.2s ease, transform 0.2s ease, background-color 0.2s ease',
        '&:hover': {
          boxShadow: 'none',
          transform: 'translateY(-1px)',
        },
        '&:active': {
          transform: 'translateY(0)',
        },
      },
      contained: {
        '&:hover': {
          boxShadow: 'none',
        },
      },
      outlined: {
        borderWidth: '1.5px',
        '&:hover': {
          borderWidth: '1.5px',
        },
      },
    },
  },
  MuiCard: {
    defaultProps: { elevation: 0 },
    styleOverrides: {
      root: {
        borderRadius: 10,
        backgroundImage: 'none',
        backgroundColor: isDark ? 'rgba(30, 36, 34, 0.92)' : 'rgba(255, 255, 255, 0.88)',
        border: isDark ? '1px solid rgba(213, 219, 227, 0.08)' : '1px solid rgba(74, 85, 104, 0.08)',
        boxShadow: 'none',
        transition: 'border-color 0.2s ease, transform 0.2s ease',
      },
    },
  },
  MuiPaper: {
    defaultProps: { elevation: 0 },
    styleOverrides: {
      root: {
        borderRadius: 10,
        backgroundImage: 'none',
        backgroundColor: isDark ? 'rgba(30, 36, 34, 0.92)' : 'rgba(255, 255, 255, 0.88)',
        border: isDark ? '1px solid rgba(213, 219, 227, 0.08)' : '1px solid rgba(74, 85, 104, 0.08)',
      },
    },
  },
  MuiAppBar: {
    defaultProps: { elevation: 0, color: 'transparent' },
    styleOverrides: {
      root: {
        backgroundImage: 'none',
        backgroundColor: isDark ? 'rgba(22, 27, 25, 0.92)' : 'rgba(243, 246, 244, 0.92)',
        borderBottom: isDark ? '1px solid rgba(213, 219, 227, 0.08)' : '1px solid rgba(74, 85, 104, 0.1)',
        boxShadow: 'none',
        color: 'inherit',
      },
    },
  },
  MuiDrawer: {
    styleOverrides: {
      paper: {
        backgroundImage: 'none',
        backgroundColor: isDark ? '#1A201D' : '#F7FAF8',
        borderRight: isDark ? '1px solid rgba(213, 219, 227, 0.08)' : '1px solid rgba(74, 85, 104, 0.1)',
      },
    },
  },
  MuiChip: {
    styleOverrides: {
      root: {
        borderRadius: 8,
        fontWeight: 500,
        fontSize: '0.8125rem',
        height: 28,
      },
    },
  },
  MuiTextField: {
    styleOverrides: {
      root: {
        '& .MuiOutlinedInput-root': {
          borderRadius: 10,
          backgroundColor: isDark ? 'rgba(22, 27, 25, 0.5)' : 'rgba(255, 255, 255, 0.7)',
          transition: 'border-color 0.2s ease, background-color 0.2s ease',
          '& fieldset': {
            borderColor: isDark ? 'rgba(213, 219, 227, 0.14)' : 'rgba(74, 85, 104, 0.16)',
          },
          '&:hover fieldset': {
            borderColor: isDark ? 'rgba(213, 219, 227, 0.28)' : 'rgba(74, 85, 104, 0.28)',
          },
          '&.Mui-focused fieldset': {
            borderColor: colors.primary.main,
            borderWidth: '1.5px',
          },
        },
      },
    },
  },
  MuiListItemButton: {
    styleOverrides: {
      root: {
        borderRadius: 10,
        margin: '2px 8px',
        transition: 'background-color 0.2s ease, transform 0.2s ease',
        '&:hover': {
          backgroundColor: isDark ? 'rgba(79, 122, 92, 0.14)' : 'rgba(79, 122, 92, 0.08)',
        },
        '&.Mui-selected': {
          backgroundColor: colors.primary.main,
          color: '#FFFFFF',
          '&:hover': {
            backgroundColor: colors.primary.dark,
          },
          '& .MuiListItemIcon-root': {
            color: '#FFFFFF',
          },
        },
      },
    },
  },
  MuiIconButton: {
    styleOverrides: {
      root: {
        transition: 'background-color 0.2s ease, opacity 0.2s ease',
        '&:hover': {
          backgroundColor: isDark ? 'rgba(213, 219, 227, 0.08)' : 'rgba(74, 85, 104, 0.06)',
        },
      },
    },
  },
  MuiDialog: {
    styleOverrides: {
      paper: {
        borderRadius: 10,
        backgroundImage: 'none',
      },
    },
  },
  MuiMenu: {
    styleOverrides: {
      paper: {
        borderRadius: 10,
        backgroundImage: 'none',
      },
    },
  },
})

export const lightTheme = createTheme({
  spacing: 8,
  palette: {
    mode: 'light',
    primary: colors.primary,
    secondary: colors.secondary,
    success: colors.success,
    error: colors.error,
    warning: colors.warning,
    info: colors.info,
    background: {
      default: '#F3F6F4',
      paper: '#FFFFFF',
    },
    text: {
      primary: '#1E293B',
      secondary: '#5A6577',
      disabled: '#94A3B0',
    },
    divider: 'rgba(74, 85, 104, 0.12)',
  },
  typography,
  shape: { borderRadius: 8 },
  shadows: softShadows,
  components: buildComponents(false),
})

export const darkTheme = createTheme({
  spacing: 8,
  palette: {
    mode: 'dark',
    primary: {
      ...colors.primary,
      main: '#7FA98A',
      light: '#A8C5B0',
      dark: '#4F7A5C',
    },
    secondary: {
      ...colors.secondary,
      main: '#A0AEC0',
      light: '#CBD5E0',
      dark: '#4A5568',
    },
    success: colors.success,
    error: colors.error,
    warning: colors.warning,
    info: colors.info,
    background: {
      default: '#161B19',
      paper: '#1E2422',
    },
    text: {
      primary: '#E8EEEA',
      secondary: '#A8B5AE',
      disabled: '#6B7872',
    },
    divider: 'rgba(213, 219, 227, 0.1)',
  },
  typography,
  shape: { borderRadius: 8 },
  shadows: softShadows,
  components: buildComponents(true),
})

export default lightTheme
