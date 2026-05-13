import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { GameTable, Player } from '../../models/models';

@Component({
  selector: 'app-lobby',
  imports: [CommonModule, FormsModule],
  templateUrl: './lobby.html',
  styleUrl: './lobby.scss'
})
export class LobbyComponent implements OnInit {
  tables: GameTable[] = [];
  player: Player | null = null;
  loadError = '';

  showCreateModal = false;
  newTableName = '';
  newTableMinBet = 1000;
  createError = '';
  creating = false;

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit() {
    const stored = localStorage.getItem('player');
    if (!stored) { this.router.navigate(['/login']); return; }
    this.player = JSON.parse(stored);
    this.loadTables();
  }

  loadTables() {
    this.loadError = '';
    this.api.getTables().subscribe({
      next: t => { this.tables = t; },
      error: err => {
        console.error('Error cargando mesas:', err);
        this.loadError = 'No se pudieron cargar las mesas. Verifica que el backend esté activo.';
      }
    });
  }

  isFull(table: GameTable): boolean {
    return table.playerCount >= table.maxPlayers;
  }

  isAdmin(): boolean {
    return this.player?.role === 'ADMIN';
  }

  join(table: GameTable) {
    if (this.isFull(table)) return;
    localStorage.setItem('tableId', table.id);
    this.router.navigate(['/bet', table.id]);
  }

  deleteTable(table: GameTable, event: MouseEvent) {
    event.stopPropagation();
    if (!confirm(`¿Seguro que quieres eliminar la mesa "${table.name}"?`)) return;
    this.api.deleteTable(table.id, this.player!.id).subscribe({
      next: () => this.loadTables(),
      error: err => alert(err.error?.error ?? 'Error al eliminar la mesa')
    });
  }

  openCreateModal() {
    this.newTableName = '';
    this.newTableMinBet = 1000;
    this.createError = '';
    this.showCreateModal = true;
  }

  closeCreateModal() {
    this.showCreateModal = false;
  }

  submitCreateTable() {
    if (!this.newTableName.trim() || !this.player) return;
    this.creating = true;
    this.createError = '';
    this.api.createTable(this.newTableName.trim(), this.newTableMinBet, this.player.id).subscribe({
      next: (newTable) => {
        this.tables = [...this.tables, newTable];
        this.closeCreateModal();
        this.creating = false;
        this.loadTables();
      },
      error: err => {
        this.createError = err.error?.error ?? 'Error al crear la mesa';
        this.creating = false;
      }
    });
  }

  logout() {
    localStorage.removeItem('player');
    localStorage.removeItem('tableId');
    localStorage.removeItem('bet');
    this.router.navigate(['/login']);
  }

  formatCOP(value: number): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency', currency: 'COP', minimumFractionDigits: 0
    }).format(value);
  }
}
