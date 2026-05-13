import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { finalize, timeout } from 'rxjs/operators';
import { ApiService } from '../../services/api.service';
import { Player } from '../../models/models';

interface Chip {
  label: string;
  value: number;
  color: string;
  textColor: string;
}

@Component({
  selector: 'app-bet',
  imports: [CommonModule],
  templateUrl: './bet.html',
  styleUrl: './bet.scss'
})
export class BetComponent implements OnInit {
  player: Player | null = null;
  tableId = '';
  betAmount = 0;
  betChips: Chip[] = [];
  loading = false;
  minBet = 0;
  joinError = '';

  chips: Chip[] = [
    { label: '$500',    value: 500,   color: '#e2e8f0', textColor: '#1e293b' },
    { label: '$1.000',  value: 1000,  color: '#f43f5e', textColor: '#fff'    },
    { label: '$5.000',  value: 5000,  color: '#3b82f6', textColor: '#fff'    },
    { label: '$10.000', value: 10000, color: '#a855f7', textColor: '#fff'    },
    { label: '$50.000', value: 50000, color: '#f59e0b', textColor: '#1e293b' },
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private api: ApiService
  ) {}

  ngOnInit() {
    const stored = localStorage.getItem('player');
    if (!stored) { this.router.navigate(['/login']); return; }
    this.player = JSON.parse(stored);
    this.tableId = this.route.snapshot.paramMap.get('tableId') ?? '';

    this.api.getTables().subscribe(tables => {
      const table = tables.find(t => t.id === this.tableId);
      if (table) { this.minBet = table.minBet; }
    });
  }

  addChip(chip: Chip) {
    if (!this.player) return;
    if (this.betAmount + chip.value > this.player.balance) return;
    this.betAmount += chip.value;
    this.betChips.push({ ...chip });
  }

  removeLastChip() {
    if (this.betChips.length === 0) return;
    const last = this.betChips.pop()!;
    this.betAmount -= last.value;
  }

  clearBet() {
    this.betAmount = 0;
    this.betChips = [];
  }

  sitDown() {
    if (this.betAmount === 0 || !this.player || this.loading) return;
    if (this.minBet > 0 && this.betAmount < this.minBet) {
      this.joinError = 'La apuesta mínima es ' + this.formatCOP(this.minBet);
      return;
    }
    this.joinError = '';
    this.loading = true;
    localStorage.setItem('bet', this.betAmount.toString());
    this.api.joinTable(this.tableId, this.player.id).pipe(
      timeout(12000),
      finalize(() => { this.loading = false; })
    ).subscribe({
      next: () => this.router.navigate(['/game', this.tableId]),
      error: (err: { name?: string; error?: { error?: string } }) => {
        this.joinError =
          err?.name === 'TimeoutError'
            ? 'El servidor no respondió. ¿Está el backend activo (puerto 8080) y PostgreSQL?'
            : (err.error?.error ?? 'Error al unirse a la mesa');
      }
    });
  }

  goToLobby() {
    this.router.navigate(['/lobby']);
  }

  formatCOP(value: number): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency', currency: 'COP', minimumFractionDigits: 0
    }).format(value);
  }

  get visibleChips(): Chip[] {
    return this.betChips.slice(-10);
  }
}
