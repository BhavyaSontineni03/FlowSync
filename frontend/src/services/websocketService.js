import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

let stompClient = null

export const connectWebSocket = (userId, organizationId, onNotification, onExpenseUpdate, onApprovalUpdate) => {
  const socket = new SockJS('http://localhost:8080/ws')
  stompClient = new Client({
    webSocketFactory: () => socket,
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    onConnect: () => {
      console.log('WebSocket connected')
      
      // Subscribe to user-specific notifications
      stompClient.subscribe(`/queue/notifications/${userId}`, (message) => {
        const notification = JSON.parse(message.body)
        onNotification(notification)
      })
      
      // Subscribe to organization-wide updates
      stompClient.subscribe(`/topic/expenses/${organizationId}`, (message) => {
        const expenseUpdate = JSON.parse(message.body)
        onExpenseUpdate(expenseUpdate)
      })
      
      stompClient.subscribe(`/topic/approvals/${organizationId}`, (message) => {
        const approvalUpdate = JSON.parse(message.body)
        onApprovalUpdate(approvalUpdate)
      })
    },
    onStompError: (frame) => {
      console.error('WebSocket error:', frame)
    },
  })
  
  stompClient.activate()
  
  return stompClient
}

export const disconnectWebSocket = () => {
  if (stompClient) {
    stompClient.deactivate()
    stompClient = null
  }
}

export default { connectWebSocket, disconnectWebSocket }

