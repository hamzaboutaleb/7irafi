export interface Conversation {
  id: string;
  applicationId: string;
  jobId: string;
  clientId: string;
  workerId: string;
  createdAt: string;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  senderId: string;
  text: string;
  sentAt: string;
}

export type ChatConnectionState = 'disconnected' | 'connecting' | 'connected';
