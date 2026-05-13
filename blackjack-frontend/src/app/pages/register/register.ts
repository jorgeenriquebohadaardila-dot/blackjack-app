import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, CommonModule],
  templateUrl: './register.html',
  styleUrl: './register.scss'
})
export class RegisterComponent {
  username = '';
  password = '';
  confirmPassword = '';
  selectedColor = '#00e5a0';
  error = '';
  success = false;
  loading = false;

  colors = ['#00e5a0', '#6366f1', '#f43f5e', '#f59e0b', '#38bdf8', '#a78bfa'];

  constructor(private api: ApiService, private router: Router) {}

  selectColor(color: string) { this.selectedColor = color; }

  register() {
    this.error = '';
    if (!this.username.trim() || !this.password || !this.confirmPassword) {
      this.error = 'Completa todos los campos';
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.error = 'Las contraseñas no coinciden';
      return;
    }
    this.loading = true;
    this.api.register(this.username.trim(), this.password, this.confirmPassword, this.selectedColor).subscribe({
      next: () => {
        this.success = true;
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: err => {
        this.error = err.error?.error ?? 'Error al registrar';
        this.loading = false;
      }
    });
  }

  goToLogin() {
    this.router.navigate(['/login']);
  }
}
