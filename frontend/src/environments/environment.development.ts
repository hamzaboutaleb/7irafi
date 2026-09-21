export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  // Not the gateway: the gateway flavour we use cannot proxy websockets, so the
  // browser connects to chat-service directly.
  chatSocketUrl: 'ws://localhost:8085/ws',
};
