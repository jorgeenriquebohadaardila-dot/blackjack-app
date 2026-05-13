export interface Player {
  id: string;
  username: string;
  avatarColor: string;
  balance: number;
  status: string;
  bet: number;
  score: number;
  hand: string;
  seatNumber: number;
  role?: string;   // 'PLAYER' | 'ADMIN'
  token?: string;
}

export interface GameTable {
  id: string;
  name: string;
  minBet: number;
  status: string;
  playerCount: number;
  maxPlayers: number;
  players: Player[];
  dealerHand: string[];
  dealerScore: number;
  remainingCards: number;
}

export interface ActionMessage {
  playerId: string;
  tableId: string;
  action: string;
  bet?: number;
}
