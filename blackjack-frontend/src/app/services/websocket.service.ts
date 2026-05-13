import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter, take, timeout } from 'rxjs/operators';
import { GameTable } from '../models/models';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class WebsocketService {
  private client!: Client;
  tableState$ = new BehaviorSubject<GameTable | null>(null);
  gameResult$ = new BehaviorSubject<GameTable | null>(null);
  connected$ = new BehaviorSubject<boolean>(false);

  connect(tableId: string): void {
    // Limpiar conexion anterior si existe
    if (this.client) {
      this.client.deactivate();
    }
    // Resetear subjects
    this.tableState$.next(null);
    this.gameResult$.next(null);
    this.connected$.next(false);

    const sockJsUrl = environment.apiOrigin
      ? `${environment.apiOrigin.replace(/\/$/, '')}/ws`
      : `${typeof window !== 'undefined' ? window.location.origin : ''}/ws`;

    this.client = new Client({
      webSocketFactory: () => new SockJS(sockJsUrl),
      reconnectDelay: 4000,
      onConnect: () => {
        this.connected$.next(true);
        this.client.subscribe('/topic/table/' + tableId, (msg: IMessage) => {
          this.tableState$.next(JSON.parse(msg.body));
        });
        this.client.subscribe('/topic/table/' + tableId + '/result', (msg: IMessage) => {
          this.gameResult$.next(JSON.parse(msg.body));
        });
      },
      onDisconnect: () => {
        this.connected$.next(false);
      }
    });
    this.client.activate();
  }

  /**
   * Publica en STOMP; si STOMP aún no conectó, espera unos segundos (evita clics sin efecto).
   */
  send(destination: string, body: unknown): void {
    const dest = '/app/' + destination;
    const payload = JSON.stringify(body);
    const publish = () => this.client.publish({ destination: dest, body: payload });
    if (this.client?.connected) {
      publish();
      return;
    }
    const sub: Subscription = this.connected$
      .pipe(filter(Boolean), take(1), timeout(6000))
      .subscribe({
        next: () => {
          publish();
          sub.unsubscribe();
        },
        error: () => sub.unsubscribe(),
      });
  }

  disconnect(): void {
    this.connected$.next(false);
    this.client?.deactivate();
  }
}
