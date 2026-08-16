// Quiet surface helpers (replaces heavy glassmorphism).
// getGlassStyles kept as alias so secondary pages keep working.

export const getSurfaceStyles = (isDark, variant = 'default') => {
  const base = {
    backgroundImage: 'none',
    boxShadow: 'none',
    transition: 'border-color 0.2s ease, background-color 0.2s ease, transform 0.2s ease, opacity 0.2s ease',
  }

  const surfaces = {
    navigation: {
      ...base,
      borderRadius: 0,
      background: isDark ? 'rgba(22, 27, 25, 0.96)' : 'rgba(247, 250, 248, 0.96)',
      border: isDark
        ? '1px solid rgba(213, 219, 227, 0.08)'
        : '1px solid rgba(74, 85, 104, 0.1)',
    },
    // Use px strings: MUI sx multiplies bare numbers by theme.shape.borderRadius.
    card: {
      ...base,
      borderRadius: '10px',
      background: isDark ? 'rgba(30, 36, 34, 0.92)' : 'rgba(255, 255, 255, 0.92)',
      border: isDark
        ? '1px solid rgba(213, 219, 227, 0.08)'
        : '1px solid rgba(74, 85, 104, 0.08)',
    },
    stat: {
      ...base,
      borderRadius: '8px',
      background: isDark ? 'rgba(30, 36, 34, 0.85)' : 'rgba(255, 255, 255, 0.85)',
      border: isDark
        ? '1px solid rgba(213, 219, 227, 0.08)'
        : '1px solid rgba(74, 85, 104, 0.08)',
    },
    default: {
      ...base,
      borderRadius: '10px',
      background: isDark ? 'rgba(30, 36, 34, 0.9)' : 'rgba(255, 255, 255, 0.9)',
      border: isDark
        ? '1px solid rgba(213, 219, 227, 0.08)'
        : '1px solid rgba(74, 85, 104, 0.08)',
    },
  }

  return surfaces[variant] || surfaces.default
}

/** @deprecated Use getSurfaceStyles */
export const getGlassStyles = getSurfaceStyles

export const smoothTransitions = {
  quick: 'opacity 0.2s ease, transform 0.2s ease',
  standard: 'opacity 0.25s ease, transform 0.25s ease',
  hover: 'opacity 0.2s ease, transform 0.2s ease, border-color 0.2s ease',
  page: 'opacity 0.3s ease, transform 0.3s ease',
}

// Intentional motion only: opacity + translateY
export const motionPresets = {
  pageInitial: { opacity: 0, y: 10 },
  pageAnimate: { opacity: 1, y: 0 },
  pageTransition: {
    duration: 0.28,
    ease: [0.22, 1, 0.36, 1],
  },
  cardInitial: { opacity: 0, y: 8 },
  cardAnimate: { opacity: 1, y: 0 },
  cardTransition: {
    duration: 0.24,
    ease: [0.22, 1, 0.36, 1],
  },
  staggerContainer: {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: { staggerChildren: 0.05, delayChildren: 0.04 },
    },
  },
  staggerItem: {
    hidden: { opacity: 0, y: 8 },
    show: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.24, ease: [0.22, 1, 0.36, 1] },
    },
  },
}
