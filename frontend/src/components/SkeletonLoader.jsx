import React from 'react'
import { Skeleton, Box, Card, CardContent } from '@mui/material'

export const StatCardSkeleton = () => (
  <Card>
    <CardContent>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box sx={{ flex: 1 }}>
          <Skeleton variant="text" width="40%" height={20} />
          <Skeleton variant="text" width="60%" height={40} sx={{ mt: 1 }} />
        </Box>
        <Skeleton variant="circular" width={50} height={50} />
      </Box>
    </CardContent>
  </Card>
)

export const TableSkeleton = ({ rows = 5, columns = 6 }) => (
  <Box>
    {Array.from({ length: rows }).map((_, index) => (
      <Box key={index} sx={{ display: 'flex', gap: 2, mb: 2 }}>
        {Array.from({ length: columns }).map((_, colIndex) => (
          <Skeleton key={colIndex} variant="rectangular" width="100%" height={40} />
        ))}
      </Box>
    ))}
  </Box>
)

export const ExpenseCardSkeleton = () => (
  <Card sx={{ mb: 2 }}>
    <CardContent>
      <Skeleton variant="text" width="60%" height={24} />
      <Skeleton variant="text" width="40%" height={20} sx={{ mt: 1 }} />
      <Box sx={{ display: 'flex', gap: 2, mt: 2 }}>
        <Skeleton variant="rectangular" width={80} height={32} />
        <Skeleton variant="rectangular" width={80} height={32} />
      </Box>
    </CardContent>
  </Card>
)

export default { StatCardSkeleton, TableSkeleton, ExpenseCardSkeleton }

