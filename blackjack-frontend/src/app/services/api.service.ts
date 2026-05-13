import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GameTable, Player } from '../models/models';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ApiService {
  /** Origen vacío = rutas relativas /api (proxy en dev o mismo host en prod). */
  private readonly base = environment.apiOrigin
    ? `${environment.apiOrigin.replace(/\/$/, '')}/api`
    : '/api';

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<Player> {
    return this.http.post<Player>(this.base + '/login', { username, password });
  }

  register(username: string, password: string, confirmPassword: string, avatarColor: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(this.base + '/register', { username, password, confirmPassword, avatarColor });
  }

  getTables(): Observable<GameTable[]> {
    return this.http.get<GameTable[]>(this.base + '/tables');
  }

  getTable(tableId: string): Observable<GameTable> {
    return this.http.get<GameTable>(`${this.base}/tables/${tableId}`);
  }

  /** Reinicia la mesa por HTTP (sirve aunque el WebSocket no esté conectado). */
  resetTableRest(tableId: string): Observable<GameTable> {
    return this.http.post<GameTable>(`${this.base}/tables/${tableId}/reset`, {});
  }

  joinTable(tableId: string, playerId: string): Observable<GameTable> {
    return this.http.post<GameTable>(this.base + '/tables/' + tableId + '/join', { playerId });
  }

  createTable(name: string, minBet: number, playerId: string): Observable<GameTable> {
    return this.http.post<GameTable>(this.base + '/tables', { name, minBet, playerId });
  }

  deleteTable(tableId: string, playerId: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(
      this.base + '/tables/' + tableId + '?playerId=' + playerId
    );
  }
}
