import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, CommonModule],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class LoginComponent {
  username = '';
  password = '';
  error = '';
  loading = false;

  constructor(private api: ApiService, private router: Router) {}

  login() {
    this.error = '';
    if (!this.username.trim() || !this.password) {
      this.error = 'Completa todos los campos';
      return;
    }
    this.loading = true;
    this.api.login(this.username.trim(), this.password).subscribe({
      next: player => {
        localStorage.setItem('player', JSON.stringify(player));
        this.router.navigate(['/lobby']);
      },
      error: err => {
        this.error = err.error?.error ?? 'Error al iniciar sesión';
        this.loading = false;
      }
    });
  }

  goToRegister() {
    this.router.navigate(['/register']);
  }
}
