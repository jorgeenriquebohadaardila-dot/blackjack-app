import { Component, OnDestroy, NgZone, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Observable, of, Subscription } from 'rxjs';
import { catchError, filter, finalize, map, take, timeout } from 'rxjs/operators';
import { WebsocketService } from '../../services/websocket.service';
import { ApiService } from '../../services/api.service';
import { GameTable, Player } from '../../models/models';

export interface CardDisplay {
  rank: string;
  suit: string;
  isRed: boolean;
  hidden: boolean;
}

@Component({
  selector: 'app-game',
  imports: [CommonModule],
  templateUrl: './game.html',
  styleUrl: './game.scss'
})
export class GameComponent implements OnInit, OnDestroy {
  /** Debe coincidir con ROUND_BREAK_SECONDS en el backend (pausa antes de nueva mano). */
  readonly roundBreakSeconds = 12;

  /** Estado de la mesa en señal: cada mensaje WS repinta sin tener que pulsar dos veces. */
  readonly tableState = signal<GameTable | null>(null);
  player: Player | null = null;
  tableId = '';
  /** Señales: la UI se actualiza aunque HTTP/WS ejecuten fuera de Zone. */
  readonly connecting = signal(true);
  readonly connectionError = signal<string | null>(null);
  readonly showResult = signal(false);
  resultType = '';
  resultLabel = '';
  nextRoundCountdown = 0;

  private subs: Subscription[] = [];
  private betSent = false;
  private connectTimeout: ReturnType<typeof setTimeout> | undefined;
  private loadFailsafe: ReturnType<typeof setTimeout> | undefined;
  private ghostBetTimer: ReturnType<typeof setTimeout> | undefined;
  private static readonly HTTP_MS = 8000;
  private countdownInterval: ReturnType<typeof setInterval> | undefined;
  /** Tras un resultado, al recibir mesa en WAITING (reset del servidor) vamos a elegir apuesta. */
  private pendingBetAfterBreak = false;
  private betRedirectGuard = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private ws: WebsocketService,
    private api: ApiService,
    private ngZone: NgZone
  ) {}

  ngOnInit() {
    const stored = localStorage.getItem('player');
    if (!stored) { this.router.navigate(['/login']); return; }
    this.player = JSON.parse(stored);
    this.tableId = this.route.snapshot.paramMap.get('tableId') ?? '';
    this.betSent = false;
    this.showResult.set(false);
    this.pendingBetAfterBreak = false;
    this.betRedirectGuard = false;
    this.connectionError.set(null);
    this.connecting.set(true);

    this.loadFailsafe = setTimeout(() => {
      if (!this.connecting()) return;
      this.connecting.set(false);
      this.connectionError.set(
        'La carga tardó demasiado. Comprueba que `npm start` y el backend en 8080 estén activos.'
      );
    }, 15000);

    this.loadTableSnapshot();

    this.ws.connect(this.tableId);

    this.subs.push(
      this.ws.tableState$.subscribe(state => {
        if (!state) return;
        this.ngZone.run(() => {
          this.tableState.set(state);
          this.connecting.set(false);
          this.connectionError.set(null);
          if (this.loadFailsafe) {
            clearTimeout(this.loadFailsafe);
            this.loadFailsafe = undefined;
          }
          if (state.status === 'WAITING' || state.status === 'BETTING') {
            this.showResult.set(false);
            this.clearRoundCountdown();
          }
          if (state.status === 'WAITING' && this.pendingBetAfterBreak) {
            this.pendingBetAfterBreak = false;
            localStorage.removeItem('bet');
            this.router.navigate(['/bet', this.tableId]);
          }
          this.maybeRedirectToBet(state);
        });
      })
    );

    this.subs.push(
      this.ws.gameResult$.subscribe(result => {
        if (result) {
          this.ngZone.run(() => {
            this.tableState.set(result);
            this.connecting.set(false);
            this.connectionError.set(null);
            if (this.loadFailsafe) {
              clearTimeout(this.loadFailsafe);
              this.loadFailsafe = undefined;
            }
            this.computeResult(result);
          });
        }
      })
    );

    const bet = parseInt(localStorage.getItem('bet') ?? '0', 10);
    if (bet > 0 && this.player) {
      const betSub = this.ws.connected$.pipe(
        filter(c => c),
        take(1)
      ).subscribe(() => {
        if (this.betSent) return;
        this.betSent = true;
        this.connectTimeout = setTimeout(() => {
          this.ws.send('place-bet', {
            playerId: this.player!.id,
            tableId: this.tableId,
            action: 'BET',
            bet
          });
          localStorage.removeItem('bet');
        }, 1200);
      });
      this.subs.push(betSub);
    }

    // Si sigues en mesa sin apuesta registrada (p. ej. apuesta fantasma en localStorage), forzar flujo a /bet
    this.ghostBetTimer = setTimeout(() => {
      const me = this.getMyPlayer();
      const st = this.tableState()?.status;
      if (!me || me.bet > 0 || (st !== 'WAITING' && st !== 'BETTING')) return;
      const ghost = parseInt(localStorage.getItem('bet') ?? '0', 10);
      if (ghost > 0) localStorage.removeItem('bet');
      this.betRedirectGuard = false;
      if (this.tableState()) this.maybeRedirectToBet(this.tableState()!);
    }, 5000);
  }

  ngOnDestroy() {
    if (this.connectTimeout) clearTimeout(this.connectTimeout);
    if (this.loadFailsafe) clearTimeout(this.loadFailsafe);
    if (this.ghostBetTimer) clearTimeout(this.ghostBetTimer);
    this.clearRoundCountdown();
    this.subs.forEach(s => s.unsubscribe());
    this.ws.disconnect();
  }

  private clearRoundCountdown() {
    if (this.countdownInterval) {
      clearInterval(this.countdownInterval);
      this.countdownInterval = undefined;
    }
  }

  /** GET mesa con tiempo máximo; si falla, intenta listado de mesas. */
  private fetchInitialTable(): Observable<GameTable | null> {
    return this.api.getTable(this.tableId).pipe(
      timeout(GameComponent.HTTP_MS),
      catchError(() =>
        this.api.getTables().pipe(
          timeout(GameComponent.HTTP_MS),
          map(tables => tables.find(x => x.id === this.tableId) ?? null),
          catchError(() => of(null))
        )
      )
    );
  }

  private loadTableSnapshot() {
    this.fetchInitialTable().subscribe({
      next: t => {
        if (t) {
          this.tableState.set(t);
          if (t.status === 'WAITING' || t.status === 'BETTING') this.showResult.set(false);
          this.connectionError.set(null);
        } else {
          this.connectionError.set(
            'No hubo respuesta del servidor o la mesa no existe. Comprueba PostgreSQL, backend en 8080 y `npm start` (proxy).'
          );
        }
        this.connecting.set(false);
        if (this.loadFailsafe) {
          clearTimeout(this.loadFailsafe);
          this.loadFailsafe = undefined;
        }
      },
      error: () => {
        this.connectionError.set(
          'Error al contactar el servidor. Revisa la petición a /api/tables/... en la pestaña Red.'
        );
        this.connecting.set(false);
        if (this.loadFailsafe) {
          clearTimeout(this.loadFailsafe);
          this.loadFailsafe = undefined;
        }
      }
    });
  }

  retryConnect() {
    this.connectionError.set(null);
    this.connecting.set(true);
    this.betRedirectGuard = false;
    if (this.loadFailsafe) clearTimeout(this.loadFailsafe);
    this.loadFailsafe = setTimeout(() => {
      if (!this.connecting()) return;
      this.connecting.set(false);
      this.connectionError.set(
        'La carga tardó demasiado. Comprueba que `npm start` y el backend en 8080 estén activos.'
      );
    }, 15000);
    this.ws.disconnect();
    this.ws.connect(this.tableId);
    this.loadTableSnapshot();
  }

  abandonToLobbyWhileLoading() {
    if (this.connectTimeout) clearTimeout(this.connectTimeout);
    if (this.loadFailsafe) clearTimeout(this.loadFailsafe);
    this.loadFailsafe = undefined;
    this.pendingBetAfterBreak = false;
    this.betRedirectGuard = false;
    this.connecting.set(false);
    this.connectionError.set(null);
    this.ws.disconnect();
    localStorage.removeItem('bet');
    this.router.navigate(['/lobby']);
  }

  computeResult(table: GameTable) {
    const me = table.players.find(p => String(p.id) === String(this.player?.id));
    if (!me) return;
    const st = (me.status || '').toUpperCase();
    if (st === 'BUST') {
      this.resultType = 'bust';
      this.resultLabel = '¡TE PASASTE!';
    } else if (st === 'BLACKJACK' || st === 'WIN' || st === 'DEALER_BUST') {
      this.resultType = 'win';
      this.resultLabel = st === 'BLACKJACK' ? '¡BLACKJACK!' : '¡GANASTE!';
    } else if (st === 'PUSH') {
      this.resultType = 'push';
      this.resultLabel = 'EMPATE';
    } else if (st === 'LOSE') {
      this.resultType = 'lose';
      this.resultLabel = 'PERDISTE';
    } else if (me.score > 21) {
      this.resultType = 'bust';
      this.resultLabel = '¡TE PASASTE!';
    } else if (table.dealerScore > 21 || me.score > table.dealerScore) {
      this.resultType = 'win';
      this.resultLabel = '¡GANASTE!';
    } else if (me.score === table.dealerScore) {
      this.resultType = 'push';
      this.resultLabel = 'EMPATE';
    } else {
      this.resultType = 'lose';
      this.resultLabel = 'PERDISTE';
    }
    this.showResult.set(true);
    this.pendingBetAfterBreak = true;
    this.nextRoundCountdown = this.roundBreakSeconds;
    this.clearRoundCountdown();
    this.countdownInterval = setInterval(() => {
      this.nextRoundCountdown--;
      if (this.nextRoundCountdown <= 0) {
        this.clearRoundCountdown();
        this.api.getTable(this.tableId).subscribe({
          next: t => {
            this.tableState.set(t);
            if (t.status === 'WAITING' && this.pendingBetAfterBreak) {
              this.pendingBetAfterBreak = false;
              localStorage.removeItem('bet');
              this.showResult.set(false);
              this.router.navigate(['/bet', this.tableId]);
            }
          },
          error: () => {}
        });
      }
    }, 1000);
  }

  hit() {
    if (!this.player || !this.isMyTurn()) return;
    this.ws.send('hit', { playerId: this.player.id, tableId: this.tableId, action: 'HIT' });
  }

  stand() {
    if (!this.player || !this.isMyTurn()) return;
    this.ws.send('stand', { playerId: this.player.id, tableId: this.tableId, action: 'STAND' });
  }

  double() {
    if (!this.player || !this.isMyTurn() || !this.canDouble()) return;
    this.ws.send('double', { playerId: this.player.id, tableId: this.tableId, action: 'DOUBLE' });
  }

  closeResult() {
    this.showResult.set(false);
    this.pendingBetAfterBreak = false;
    this.betRedirectGuard = false;
    this.clearRoundCountdown();
    this.api.resetTableRest(this.tableId).pipe(
      finalize(() => {
        localStorage.removeItem('bet');
        this.router.navigate(['/bet', this.tableId]);
      })
    ).subscribe({ error: () => {} });
  }

  goToLobby() {
    this.showResult.set(false);
    this.pendingBetAfterBreak = false;
    this.betRedirectGuard = false;
    this.clearRoundCountdown();
    this.api.resetTableRest(this.tableId).pipe(
      finalize(() => {
        localStorage.removeItem('bet');
        this.ws.disconnect();
        this.router.navigate(['/lobby']);
      })
    ).subscribe({ error: () => {} });
  }

  isMyTurn(): boolean {
    const me = this.tableState()?.players.find(p => String(p.id) === String(this.player?.id));
    return me?.status === 'PLAYING';
  }

  canDouble(): boolean {
    if (!this.isMyTurn()) return false;
    const me = this.getMyPlayer();
    if (!me?.hand) return false;
    const cards = this.parseCards(me.hand);
    const visible = cards.filter(c => c && c !== 'XX' && c !== '??');
    return visible.length === 2;
  }

  getMyPlayer(): Player | undefined {
    return this.tableState()?.players.find(p => String(p.id) === String(this.player?.id));
  }

  getSeats(): (Player | null)[] {
    const seats: (Player | null)[] = Array(6).fill(null);
    (this.tableState()?.players ?? []).forEach(p => {
      const num = p.seatNumber ?? 1;
      const idx = Math.max(0, Math.min(5, num - 1));
      seats[idx] = p;
    });
    return seats;
  }

  /** Cartas del crupier: siempre array de códigos (evita *ngFor sobre string JSON). */
  dealerCardList(): string[] {
    return this.normalizeCardCodes(this.tableState()?.dealerHand as unknown);
  }

  /** Mesa en espera de apuesta: mostrar aviso con botón (no depender solo de redirección automática). */
  needsBetBanner(): boolean {
    const st = this.tableState()?.status;
    if (st !== 'WAITING' && st !== 'BETTING') return false;
    const me = this.getMyPlayer();
    if (!me) return false;
    return me.bet === 0 && (me.status === 'WAITING' || me.status === 'BETTING');
  }

  goBetScreen(): void {
    this.betRedirectGuard = false;
    this.router.navigate(['/bet', this.tableId], { replaceUrl: true });
  }

  samePlayerId(a: string, b: string): boolean {
    return String(a) === String(b);
  }

  private maybeRedirectToBet(state: GameTable) {
    if (!this.player || this.showResult()) return;
    const me = state.players?.find(p => String(p.id) === String(this.player!.id));
    if (!me) return;
    if (state.status === 'PLAYING' || state.status === 'FINISHED') {
      this.betRedirectGuard = false;
      return;
    }
    const needBet =
      me.bet === 0 &&
      (me.status === 'WAITING' || me.status === 'BETTING') &&
      (state.status === 'WAITING' || state.status === 'BETTING');
    if (!needBet) {
      this.betRedirectGuard = false;
      return;
    }
    if (this.betRedirectGuard) return;
    this.betRedirectGuard = true;
    queueMicrotask(() => {
      this.router.navigate(['/bet', this.tableId], { replaceUrl: true }).finally(() => {
        setTimeout(() => {
          this.betRedirectGuard = false;
        }, 800);
      });
    });
  }

  private normalizeCardCodes(raw: unknown): string[] {
    if (raw == null) return [];
    if (Array.isArray(raw)) {
      return (raw as unknown[]).map(x => this.toCompactCardString(x)).filter((s): s is string => !!s);
    }
    if (typeof raw === 'string') {
      const t = raw.trim();
      if (t.includes(',') && !t.startsWith('[')) {
        return t.split(',').map(x => this.toCompactCardString(x.trim())).filter((s): s is string => !!s);
      }
      if (t.startsWith('[')) {
        try {
          const arr = JSON.parse(t) as unknown[];
          return arr.map(x => this.toCompactCardString(x)).filter((s): s is string => !!s);
        } catch {
          return [];
        }
      }
      if (t.length >= 2) return [this.toCompactCardString(t)];
    }
    return [];
  }

  private toCompactCardString(x: unknown): string {
    if (typeof x === 'string') {
      const s = x.trim();
      if (!s || s === 'XX' || s === '??') return s;
      const m = s.match(/^((?:10)|[A2-9JQK])([HDCS])$/i);
      if (m) {
        const r = m[1].toUpperCase() === '10' ? '10' : m[1].toUpperCase();
        return r + m[2].toUpperCase();
      }
      return s;
    }
    if (x && typeof x === 'object') {
      const o = x as { rank?: string; suit?: string };
      return this.legacyCardToCompact(o);
    }
    return '';
  }

  private legacyCardToCompact(o: { rank?: string; suit?: string }): string {
    const rank = (o.rank ?? '').toUpperCase();
    const suit = (o.suit ?? '').toUpperCase();
    const rMap: Record<string, string> = {
      ACE: 'A', TWO: '2', THREE: '3', FOUR: '4', FIVE: '5', SIX: '6', SEVEN: '7', EIGHT: '8', NINE: '9',
      TEN: '10', JACK: 'J', QUEEN: 'Q', KING: 'K',
    };
    const sMap: Record<string, string> = { HEARTS: 'H', DIAMONDS: 'D', CLUBS: 'C', SPADES: 'S' };
    const rr = rMap[rank];
    const ss = sMap[suit];
    if (!rr || !ss) return '';
    return rr + ss;
  }

  parseCards(hand: unknown): string[] {
    return this.normalizeCardCodes(hand);
  }

  parseCard(card: string): CardDisplay {
    if (!card || !String(card).trim() || card === 'XX' || card === '??' || card === 'HIDDEN') {
      return { rank: '', suit: '', isRed: false, hidden: true };
    }
    const last = card.slice(-1).toUpperCase();
    const rank = card.length > 1 ? card.slice(0, -1) : card;
    const suitMap: Record<string, string> = { H: '♥', D: '♦', S: '♠', C: '♣' };
    return { rank, suit: suitMap[last] ?? last, isRed: last === 'H' || last === 'D', hidden: false };
  }

  formatCOP(value: number): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency', currency: 'COP', minimumFractionDigits: 0
    }).format(value);
  }

  translateStatus(status: string | undefined): string {
    const map: Record<string, string> = {
      WAITING: 'ESPERANDO', PLAYING: 'EN JUEGO',
      FINISHED: 'TERMINADO', BETTING: 'APOSTANDO',
      STAND: 'PLANTADO', BUST: 'PASADO', BLACKJACK: 'BLACKJACK',
      WIN: 'GANA', LOSE: 'PIERDE', PUSH: 'EMPATE', DEALER_BUST: 'GANA'
    };
    return map[status ?? ''] ?? (status ?? 'ESPERANDO');
  }
}
