import React from 'react'
import { Card } from '@mui/material'
import { motion } from 'framer-motion'

const MotionCard = motion(Card)

export const AnimatedCard = React.memo(({ children, delay = 0, ...props }) => {
  return (
    <MotionCard
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, delay, ease: [0.4, 0, 0.2, 1] }}
      whileHover={{ y: -2 }}
      {...props}
    >
      {children}
    </MotionCard>
  )
})

export default AnimatedCard

